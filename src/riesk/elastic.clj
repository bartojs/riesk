(ns riesk.elastic
  (:require [clojure.tools.logging :refer (info error debug warn)]
            [clojure.edn :as edn]
            [clojurewerkz.elastisch.native :as es]
            [clojurewerkz.elastisch.native.index :as idx]
            [clojurewerkz.elastisch.native.document :as doc]
            [riemann.common :as comm])
  (:import java.text.SimpleDateFormat java.io.File [java.util Date TimeZone]))

(def dateformats (reduce-kv #(assoc %1 %2 (doto (SimpleDateFormat. %3) (.setTimeZone (TimeZone/getTimeZone "UTC")))) {} 
                            {:day "yyyy.MM.dd" :hour "yyyy.MM.dd.HH"  :week "yyyy.MM.dd.ww"  :month "yyyy.MM"  :year "yyyy.MM"}))  

(defn- gettime [event]
  (comm/unix-to-iso8601 (or (:time event) (quot (System/currentTimeMillis) 1000))))

(defn- settimestamp [event]
  (dissoc (if (contains? event "@timestamp") 
             event 
             (assoc event "@timestamp" (or (:isotime event) (gettime event))))
          :isotime :time :ttl))

(defn- setid [event]
  (merge event (if-let [id (get event "_id")] {:_id id})))

(defn- getedn [event k]
  (let [v (get event k)]
    (try (edn/read-string v)
       (catch Exception e 
         (warn "Unable to read supposed EDN form with value: " v " for " (name k) " in " (str event)) 
         v))))

(defn- normalize [event]
  (reduce-kv (fn [x k v]
               (if (and (not= "_id" (name k)) (.startsWith (name k) "_"))
                    (assoc x (subs (name k) 1) (getedn event k)) 
                    (assoc x k v))) 
             {} event))

(defn connect [nodes opts] 
  (es/connect (or nodes [["localhost" 9300]]) (or opts {"cluster.name" "riesk-cluster"})))

(defn load-template [connection template-name template-file]
  (idx/create-template connection template-name (edn/read-string (slurp template-file))))

(defn index [connection doctype & {:keys [index-name series normalize?] :or {index-name "logstash" series :day normalize? true}}]
  (fn [events]
    (doseq [event events]
      (let [evt (if normalize? (-> event settimestamp normalize) (-> event settimestamp))
            idxname (format "%s-%s" index-name (.format (dateformats series) (comm/iso8601->unix (get evt "@timestamp"))))]
        (try (doc/create connection idxname doctype evt)
          (catch Exception e 
            (error "Unable to index:" evt e)))))))
