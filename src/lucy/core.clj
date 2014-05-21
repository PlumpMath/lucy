(ns lucy.core
  "The core DSL for expressing the cypher query language; uses hiccup to represent the cypher AST"
  (:refer-clojure :exclude [count])
  (:require
   [lucy.engine :as eng]
   [clojure.java.jdbc :as jdbc]
   [taoensso.timbre :refer [spy]]
   [slingshot.slingshot :refer [throw+]]))

(defn create-index*
  "Create an index on a specified label with a specified property"
  [label property]
  [:create-index {:label label
                  :property property}])

(defmacro create-index
  [label property]
  (create-index* label (name property)))

(defn- node-property?
  "Checks to see if something is a node property"
  [x]
  (or (map? x) (and (sequential? x) (= :values (first x)))))

(defn neo4j-object*
  ;; TODO: support multiple labels?
  ([] [])
  ([x] [(cond
         ((some-fn string? symbol?) x) {:name (str x)}
         (keyword? x) {:label x}
         (node-property? x) {:properties x}
         :else (throw+ {:type ::bad-node :args [x]}))])

  ([x y] [(cond
           (and ((some-fn string? symbol?) x) (keyword? y))
           {:name x :label y}
           
           (and ((some-fn string? symbol?) x) (node-property? y))
           {:name x :properties y}

           (and (keyword? x) (node-property? y))
           {:label x :properties y}
           
           :else
           (throw+ {:type ::bad-node :args [x y]}))])

  ([x y z] [(if (and ((some-fn string? symbol?) x)
                     (keyword? y)
                     (node-property? z))
              {:name x :label y :properties z}
              (throw+ {:type ::bad-node :args [x y z]}))]))

(defmacro neo4j-object
  ([] (neo4j-object*))
  ([x & args]
     (if (symbol? x) 
       `(neo4j-object* ~(name x) ~@args)
       `(neo4j-object* ~x ~@args))))

(defn node*
  "Refer to a neo4j node"
  [& args] (cons :node (apply neo4j-object* args)))

(defmacro node
  "Refer to a neo4j node"
  [& args] `(cons :node (neo4j-object ~@args)))
  
(defn --*
  "Create an undirected arrow"
  [& args] (cons :-- (apply neo4j-object* args)))

(defmacro --
  "Create an undirected arrow"
  [& args] `(cons :-- (neo4j-object ~@args)))

(defn -->*
  "Create a forward arrow"
  [& args] (cons :--> (apply neo4j-object* args)))

(defmacro -->
  "Create a forward arrow"
  [& args] `(cons :--> (neo4j-object ~@args)))

(defn <--*
  "Create a backward arrow"
  [& args] (cons :<-- (apply neo4j-object* args)))

(defmacro <--
  "Create a backward arrow"
  [& args] `(cons :<-- (neo4j-object ~@args)))

(defn edge*
  "Create an edge"
  [x arrow y] [:edge x arrow y])

(defmacro ^:private node-shorthand
  [n] `(cond (symbol? ~n)
            `(node ~~n)
            
            (and (= (first ~n) 'quote)
                 (sequential? (second ~n)))
            (cons 'node (second ~n))
            
            :else
            ~n))

(defmacro edge
  "Create an edge or series of edges"
  ([n] (node-shorthand n))
  ([n arrow & args]
     `(edge*
       ~(node-shorthand n)
       ~(if (#{'--> '<-- '--} arrow)
          `(~arrow)
          arrow)
       (edge ~@args))))

(defn as*
  "Rename an output field for return"
  [x y] [:as x y])

(defmacro as
  "Rename an output field for return"
  [x y]
  (let [[x y]
        (map
         (fn [w]
           (cond (symbol? w) `(variable ~w)
                 (keyword? w) `(variable ~(name w))
                 :else w))
         [x y])]
    `(as* ~x ~y)))

(defn id*
  "Get the id of an object"
  [x]
  [:id x])

(defmacro id
  "Get the id of an object"
  [x]
  `(id* ~(if (symbol? x) `(variable ~x) x)))

(defn count*
  "Get the count of a pattern"
  [x]
  [:count x])

(defmacro count
  "Get the count of a pattern"
  [x]
  `(count* ~(if (symbol? x) `(variable ~x) x)))

(defn create
  "Create a neo4j pattern"
  [& args]
  (cons :create args))

(defn optional-match
  "Matches neo4j patterns, using NULLs for missing parts of the pattern"
  [& args]
  (cons :optional-match args))

(defn match
  "Match neo4j patterns"
  [& args]
  ;; TODO: hack so this infers when we want to match nodes
  (cons :match args))

(defn variable*
  "Refer to a variable"
  [x]
  [:variable x])

(defmacro variable
  "Refer to a variable"
  [x]
  `(variable* ~(name x)))

(defn attribute
  "Get an attribute (or nested attribute) for a given variable"
  [v & attrs]
  (cond
   (not (every? (partial some-fn [symbol? string? keyword?]) attrs))
   (throw+ {:type ::bad-attribute :args (cons v attrs)})
   (some-fn [symbol? string?] v)
   (concat [:attribute [:variable (name v)]] (map name attrs))
   :else
   (concat [:attribute v] (map name attrs))))

(defn return*
  "Return from a match"
  [& args]
  (cons
   :return
   (for [a args]
     (if ((some-fn string? symbol?) a)
       (variable* (name a))
       a))))

(defmacro return
  [& args]
  `(return*
    ~@(for [a args]
        (if (symbol? a) (name a) a))))

(defn delete*
  "Delete matches"
  [& args]
  (cons
   :delete
   (for [a args]
     (if ((some-fn string? symbol?) a)
       (variable* (name a))
       a))))

(defmacro delete
  [& args]
  `(delete*
    ~@(for [a args]
        (if (symbol? a) (name a) a))))


(defn query
  "Run a JDBC query"
  ;; TODO: use a multi-method for this this?
  [db & [first-arg & other-args :as args]]
  (cond
   (and (string? first-arg) (empty? other-args))
   (jdbc/query db [first-arg])
   
   (some #{:create :create-index :merge :set :delete}
         (flatten args)) 
   (jdbc/with-db-transaction [db-transaction db]
     (jdbc/query db-transaction (apply eng/as-jdbc-query args)))
   
   :else 
   (jdbc/query db (apply eng/as-jdbc-query args))))
