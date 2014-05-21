(ns lucy.t-core
  "Test lucy's core functionality"
  (:refer-clojure :exclude [count])
  (:require
   [lucy.core :refer :all]
   [lucy.engine :refer [as-jdbc-query as-cypher]]
   [midje.sweet :refer [facts fact]]))


(defn mktestdb
  "Create a specification for a unique in-memory test database"
  []
  {
   :classname "org.neo4j.jdbc.Driver"
   :subprotocol "neo4j"
   :subname (str "mem:" (gensym))
   })

(facts
 "create-index"
 (fact
  "cypher formats correctly"
  (as-cypher (create-index :TestLabel testprop))
  => "CREATE INDEX ON :TestLabel(testprop)")
 
 (fact
  "can create indexes in Neo4J"
  (query (mktestdb)
         (create-index :TestLabel testprop))
  => '()))

(facts
 "delete"

 (fact
  "can format a simple example"
  (as-cypher (delete n))
  => "DELETE n")

 (fact
  "can format multiple deletes"
  (as-cypher (delete n,m))
  => "DELETE n , m"))

(facts
 "node"
 (fact "can be empty"
       (as-cypher (node)) => "( )")
 (fact "basic pattern works"
       (as-cypher (node n)) => "( n )")
 (fact "labels work"
       (as-cypher (node :testlabel)) => "( :testlabel )"
       (as-cypher (node n :testlabel)) => "( n :testlabel )")
 (fact "properties work"
       (as-cypher (node n {:test "test"}))
       => "( n { test : \"test\" } )"
       (as-cypher (node {1 2.999, "Hello" "Goodbye"}))
       => "( { 1 : 2.999 , Hello : \"Goodbye\" } )"))

(facts
 "match"
 (fact
  "can format an elementary match node query"
  (as-cypher
   (match
    (node n)
    (return n))) => "MATCH ( n ) RETURN n")

 (fact
  "elementary matches work"
  (query (mktestdb)
         (match
          (node n)
          (return n))) => '())
 
 (fact
  "can format a match with count"
  (as-cypher 
   (match (node :test_node)
          (return (count *))))
  => "MATCH ( :test_node ) RETURN COUNT ( * )")

 (fact
  "can count nodes"
  (query
   (mktestdb)
   (match (node n)
          (return (as :count (count *))))) =>
          [{:count 0}])

(fact
  "can format nodes using gensym names"
  (let [nom (gensym)]
    (as-cypher (node* nom)) => (str "( " nom " )")
    (as-cypher (node* nom :test)) => (str "( " nom " :test )")
    )))

(facts
 "edges"

 (fact "can format a simple, undirected edge"
       (as-cypher (edge x -- y))
       => "( x ) -[ ]- ( y )")
 
 (fact "can format several sorts of edges"
       (as-cypher (edge a -- b --> c <-- d))
       => "( a ) -[ ]- ( b ) -[ ]-> ( c ) <-[ ]- ( d )")

 (fact "can format several complex edges"
       (as-cypher (edge a (-- :awesome)
                        b (--> X :sees)
                        c (<-- {:properties? "Oh yeah"})
                        (node :nodes_work_too)))
       => "( a ) -[ :awesome ]- ( b ) -[ X :sees ]-> ( c ) <-[ { properties? : \"Oh yeah\" } ]- ( :nodes_work_too )"))

(facts
 "create"

 (fact
  "can format a simple example"
  (as-cypher
   (create
    (node n :test)))
  => "CREATE ( n :test )")

 (fact
  "can format a more complex example"
  (as-cypher
   (create
    [(edge a (--> :test) b)
     (edge a (--> :test2) d)]))

  => "CREATE ( a ) -[ :test ]-> ( b ) , ( a ) -[ :test2 ]-> ( d )")

 (fact
  "elementary create statements work"
  (query
   (mktestdb)
   (as-cypher
    (create
     (node n :test))))
  => '())

 (fact
  "elementary create statements can return values"
  (query
   (mktestdb)
   (as-cypher
    (create
     (node n :test {:test "OK!"})
     (return n.test))))
  => [{:n.test "OK!"}])

 (fact
  "can create a node without a name"
  (query
   (mktestdb)
   (create (node :TestChemical {:smiles "CCCC"})))
  => '())
 
 (fact
  "elementary create statements can return multiple values"
  (query
   (mktestdb)
   (as-cypher
    (create
     (node n :test {:first_name "Paul"
                    :last_name "Erdős"})
     (return n.first_name n.last_name))))
  => [{:n.first_name "Paul", :n.last_name "Erdős"}])

 (fact
  "can return fields using specified names"
  (query
   (mktestdb)
   (create
    (node n {:sophisticated "ಠ_ರೃ"
             :angry "≧Д≦"})
    (return
     (as sophisticated n.sophisticated)
     (as angry n.angry))))
  => [{:sophisticated "ಠ_ರೃ"
       :angry "≧Д≦"}])
 
 (fact
  "can create multiple entries"
  (query
   (mktestdb)
   (create (node n {:id 1}))
   (create (node m {:id 2})))
  => '())

 (fact
  "can create multiple entries and retrieve them"
  (let [db (mktestdb)]
    (query db
           (create (node n :test_node {:id 1}))
           (create (node m :test_node {:id 2})))
    (query db
           (match (node :test_node)
                  (return (as count (count *)))))
    => '({:count 2})))
 
 (fact
  "can create nodes using gensym names"
  (let [nom (gensym)]
    (query
     (mktestdb)
     (create (node* nom)
             (return* nom)))
    =not=> empty?))

 (fact
  "can create edges"
  (query (mktestdb) (create (edge a (--> :test) b)))
  => '())

 (fact
  "can create a series of edges"
  (query (mktestdb)
         (create
          [(edge a (--> :test) b)
           (edge a (--> :test2) d)]))
  => '()))

(facts
  "in a complex example"
  (let [db (mktestdb)]
    (fact
     "we can create nodes and relationships"
     (->>
      (query
       db
       (create [(node n :test1)
                (node m :test2)
                (edge n (--> :FOO) m)
                (edge '(a :A {:awesome "yes"})
                      (<-- :BAR)
                      '(b :test1 {:x 1 :y 2}))]
               (return a,b,n,m)))
           first
           keys
           set)
          => #{:a :b :m :n})
    
    (fact "we can match the nodes we create"
          (query db
                 (match (node n)
                        (return (as :count (count n)))))
          => [{:count 4}])
    
    (fact "we can match the relations we crated"
          (query db
                 (match (edge '() (--> r :FOO) '())
                        (return (as :count (count r)))))
          => [{:count 1}]
          
          (query db
                 (match (edge '() (--> r :BAR) '())
                        (return (as :count (count r)))))
          => [{:count 1}]
          
          (query db
                 (match (edge '() (--> r) '())
                        (return (as :count (count r)))))
          => [{:count 2}])

    (fact "we can delete nodes"
          (query
           db
           (match (node n)
                  (optional-match (edge n (-- r) m))
                  (delete n r m)
                  (return (as :count (count n)))))
          => [{:count 4}])

    (fact "we are sure the nodes are deleted"
          (query db
                 (match (node n)
                        (return (as :count (count n)))))
          => [{:count 0}])

    (fact "we are sure the relations we created are gone"
          (query db
                 (match (edge '() (--> r) '())
                        (return (as :count (count r)))))
          => [{:count 0}])))
