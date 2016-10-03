(ns dribble-stats.urlmatch
  (:require [clojure.string :as str]))

(defprotocol IPattern
  (recognize [this url]))

(defrecord Pattern [p]
  IPattern
  (recognize [this url]
    (let [[_ host] (re-find #"host\((.*?)\);" p)
          [_ path] (re-find #"path\((.*?)\);" p)
          path-binds (map second (re-seq #"\?(.*?)(/|$)" path))
          queryparams (map second (re-seq #"queryparam\((.*?)\);" p))
          queryparams-binds (map (juxt (partial re-find #"[^=]*")
                                       (comp second (partial re-find #"\?(.*)$")))
                                 queryparams)]
      (if (re-find (re-pattern (str "(http://|https://)" host "/")) url)
        (let [path-binds
              (map (fn [bind]
                     [(keyword bind)
                      (second (re-find
                               (re-pattern
                                (reduce (fn [r b]
                                          (if (= bind b) r
                                              (str/replace
                                               r (re-pattern (str "\\?" b)) "")))
                                        (if (.startsWith path (str "?" bind))
                                          (str/replace path (re-pattern (str "\\?" bind))
                                                       "\\.*/(.*?)")
                                          (str/replace path (re-pattern (str "\\?" bind))
                                                       "(.*?)(\\\\?|/|\\$)"))
                                        path-binds)) url))]) path-binds)
              queryparams-binds
              (map (fn [[key bind]]
                      [(keyword bind)
                       (get (re-find (re-pattern (str "(\\?|\\&)" key "=(.*?)(\\&|$)"))
                                     url) 2)])
                   queryparams-binds)
              binds (filter (complement empty?) (concat path-binds queryparams-binds))]
          (if (every? second binds)
            binds)
          ))
      )))
