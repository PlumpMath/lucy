(defproject lucy "0.1.0-SNAPSHOT"
  :description "DSL for Neo4J Interactivity"
  :min-lein-version "2.0.0"
  :dependencies
  [
   [org.clojure/clojure "1.6.0"]            ; Clojure
   [org.clojure/java.jdbc "0.3.3"]          ; Java Database Connectivity 
   [org.neo4j/neo4j-jdbc "2.0.1-SNAPSHOT"]  ; Neo4J JDBC Connectivity
   [com.mchange/c3p0 "0.9.2.1"]             ; Connection Pooling for JDBC
   [com.taoensso/timbre "3.2.1"]            ; Logging
   [environ "0.5.0"]                        ; Settings from environment variables
   [slingshot "0.10.3"]                     ; Enhanced exceptions
   ]
  :plugins
  [
   [lein-environ "0.5.0"]                   ; Set environ variable in project.clj
   ]
  :profiles
  {
   :dev 
   {
    :env 
    {
     ;;:db-name "//localhost:7474/"         ; RESTful Neo4J
     :db-name "mem:test-database"           ; Embedded Test Database
     }
    :plugins 
    [
     [lein-midje "3.1.1"]                   ; Testing plugin for Leiningen
     ]
    :dependencies 
    [
     [org.neo4j/neo4j-kernel "2.1.0-M01"
      :classifier "tests"]                  ; Test Neo4J Database
     [org.neo4j/neo4j-cypher "2.1.0-M01"]   ; Needed by neo4j-kernel
     [midje "1.6.3"]                        ; Testing framework
     ]}})
