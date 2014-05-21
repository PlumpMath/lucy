(ns lucy.engine
  "The engine for compiling cypher AST to cypher strings and parameter lists"
  (:refer-clojure :exclude [count])
  (:require
   [slingshot.slingshot :refer [throw+]]
   ))

(declare translate as-cypher-query)

(defn- translate-key
  [key]
  (cond
   ((some-fn symbol? keyword? string?) key) (name key)
   (number? key) (str key)
   :else (throw+ {:type ::could-not-translate-key :args [key]})))

(defn- translate-value
  [val]
  (cond
   ((some-fn symbol? number?) val) (str val)
   (string? val) (str \" val \")
   (= :value (first val)) (throw+ :type ::TODO)
   :else (throw+ {:type ::could-not-translate-value})))

(defn- translate-map
  [property-map]
  (cond
   (not (map? property-map))
   (throw+ {:type ::not-a-map :map property-map})
   
   (empty? property-map) []
   
   :else 
   ["{"
    (->> (for [[k v] property-map]
           [(translate-key k) ":" (translate-value v)])
         (interpose ","))
    "}"]))

(defn- translate-neo4j-object
  [[{:keys [:name :label :properties]
     :or {:name "" :label "" :properties {}}}]]
  [(str name) (str label) (translate-map properties)])

(defn- node
  [body]
  ["(" (translate-neo4j-object body) ")"])

(defn- create-index
  [[{:keys [:label :property]}]]
   (format "CREATE INDEX ON %s(%s)" label (name property)))

(defn- match
  [body]
  (cons "MATCH" (translate body)))

(defn- optional-match
  [args]
  (cons "OPTIONAL MATCH" (translate args)))

(defn- return
  [args]
  (->> args
       translate
       (interpose ",")
       (cons "RETURN")))

(defn- delete
  [args]
  (->> args
       translate
       (interpose ",")
       (cons "DELETE")))

(defn- create
  [[first-arg & other-args :as args]]
  (cons "CREATE"
  (cond
   (every? sequential? first-arg)
   [(interpose "," (translate first-arg))
    (translate other-args)]
   (every? (comp keyword? first) args)
   (translate args)
   :else
   (throw+ {:type ::could-not-create})
   )))
  

(defn- as
  [[x y]]
  [(translate [y]) "AS" (translate [x])])
  
(defn- variable
  [[x]]
  (if (number? x)
    (str x)
    (name x)))

(defn- attribute
  [args]
  (clojure.string/join "." (map as-cypher-query args)))

(defn- count
  [body]
  ["COUNT (" (translate body) ")"])

(defn- edge
  [[x arrow y]]
  (translate [x arrow y]))

(defn- --
  [body]
  ["-[" (translate-neo4j-object body) "]-"])

(defn- -->
  [body]
  ["-[" (translate-neo4j-object body) "]->"])

(defn- <--
  [body]
  ["<-[" (translate-neo4j-object body) "]-"])


(defmacro ^:private make-cypher-do-map
  [actions]
  (let [keyword-actions (map keyword actions)]
    `(into {} (map #(vector %1 %2)
                   [~@keyword-actions]
                   [~@actions]))))

(def ^:private cypher-do
  (make-cypher-do-map
   [
    create-index
    optional-match
    match
    node
    return
    variable
    create
    count
    as
    edge
    -->
    --
    <--
    delete
    ]))

(defn- translate
  "Recursively translate a cypher AST to a series of strings and a vector of parameters"
  [forms]
  (for [[action & body :as form] forms]
    (cond
     (string? form) form
     (sequential? action) (translate form)
     (keyword? action) ((cypher-do action) body)
     :else (throw+ :type ::unable-to-translate))))

(defn as-jdbc-query
  "Rewrite cypher ASTs as a cypher query string"
  [& forms]
   (->> forms
        translate
        flatten
        (filter (complement empty?))
        (clojure.string/join " ")
        vector))

(defn as-cypher
  "Rewrite a cypher AST as a cypher query string"
  [& forms]
  (first (apply as-jdbc-query forms)))
