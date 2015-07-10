(ns riesk.elastic
  (:require [clojure.tools.logging :refer (info error debug warn)]
            [clojure.edn :as edn]
            [clojurewerkz.elastisch.native.index :as esi]
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

(defn- setdoctype [doctype event]
  (assoc event :create (if-let [id (get event "_id")] {:_type doctype :_id id} {:_type doctype})))

(defn- getedn [event k]
  (let [v (get event k)]
    (try (edn/read-string v)
       (catch Exception e 
         (warn "Unable to read supposed EDN form with value: " v " for " (name k) " in " (str event)) 
         v))))

(defn- massage-event [event]
  (reduce-kv (fn [x k v]
               (if (and (not= "_id" (name k)) (.startsWith (name k) "_"))
                    (assoc x (subs (name k) 1) (getedn event k)) 
                    (assoc x k v))) 
             {} event))

(defn- ri->es [events massage doctype]
  (->> [events] flatten (remove streams/expired?) (map settimestamp) #(if massage (map massage-event %) %) (map (partial setdoctype doctype))))

(defn connect [url clustername] 
  (es/connect (or url "http://localhost:9200") (or clustername "riesk-cluster")))

(defn load-template [connection template-name template-file]
  (esi/create connection template-name (edn/read-string (slurp template-file))))

(defn index [connection doctype & {:keys [index timestamping massage] :or {index "logstash" massage true timestamping :day}}]
  (fn [events]
      (let [groups (group-by #(format "%s-%s" index (comm/iso8601->unix (get % "@timestamp"))) (ri->es events massage doctype))]
        (doseq [[k v] groups]
          (try
            (let [res (eb/bulk-with-index connection k v) total (count (:items res)) succ (filter :ok (:items res)) failed (filter :error (:items res))]
               (info (format "elastic index to %s total=%d success=%d fail=%d took %dms" index total (count succ) (count failed) (:took res)))
               (debug "Failed: " failed))
             (catch Exception e
                  (error "Unable to bulk index:" e)))))))
