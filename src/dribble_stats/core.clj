(ns dribble-stats.core
  (:require [clj-http.client :as client]
            [cheshire.core :as json]
            [clojure.core.async :as async :refer [<! >! <!! >!!]]))

(def token "29f5c5c8f899626c9513cf25580b340f3ddba017f0fe87c34b51e17ba472867b")

(def dribble-api "https://api.dribbble.com/v1")

(def followers-chan (async/chan 60))

(def shots-chan (async/chan 60))

(def likes-chan (async/chan))

(def echo-chan (async/chan 100))

(def done-chan (async/chan))

(def limit (ref 100))

(def reset (ref 0))

(def status (ref {}))

(def api-ready? (ref true))

(defn current-time []
  (quot (System/currentTimeMillis) 1000))

(defn wait-for-api []
  (when (<= @limit 0)
    (dosync (ref-set api-ready? false))
    (>!! echo-chan "Dribble API limit exceeded.")
   (let [timeout (* (- @reset (current-time)) 1000)]
     (if (> timeout 0) (<!! (async/timeout timeout))))
   (dosync (ref-set limit 100)
           (ref-set reset 0)
           )
   (>!! echo-chan "Resuming requests")))

(defn update-status [key data]
  #(if (key %)
     (update % key + (count data))
     (assoc % key (count data))))

(defn api-request [uri page]
  (dosync (alter limit dec))
  (when (<= @limit 0) (wait-for-api))
  (>!! echo-chan (str "GET " (str dribble-api uri) " page " page))
  (let [response (try (client/get (str dribble-api uri)
                                  {:headers {"Authorization" (str "Bearer " token)}
                                   :query-params {"per_page" "100"  "page" (if page page 1)}})
                      (catch Exception e e))
        next-url (get-in response [:links :next :href])
        data (json/parse-string (:body response) true)
        l (get-in response [:headers "X-RateLimit-Remaining"])
        r (get-in response [:headers "X-RateLimit-Reset"])]
    (if (and l r)
      (dosync (ref-set limit (Integer. l))
              (ref-set reset (Integer. r))))
    (if (= (:status response) 200)
      {:next next-url :data data}
      (recur uri page))))

(defn get-rest [uri f key page]
  (let [response (api-request uri page)]
    (f (:data response))
    (dosync (alter status (update-status key (:data response)))
            (if (> @limit 0) (ref-set api-ready? true)))
    (if (:next response)
      (recur uri f key (inc page)))))


(defn get-followers-async [user]
  (let [uri (str "/users/" user "/followers")
        key :followers
        response (api-request uri 1)
        f #(map (comp (partial >!! followers-chan) :username :follower) %)]
    (when (:next response)
      (get-rest uri f key 2))
    (dorun (f (concat (:data response))))
    (dosync (alter status (update-status key (:data response)))
            (if (> @limit 0) (ref-set api-ready? true)))))

(defn get-shots-async [user]
  (let [uri  (str "/users/" user "/shots")
        key :shots
        response (api-request uri 1)
        f #(map (comp (partial >!! shots-chan) :id) %)]
    (>!! echo-chan [user (count (:data response))])
    (when (:next response)
      (get-rest uri f key 2))
    (>!! echo-chan (str "last shots for user " user))
    (doall (f (:data response)))
    (dosync (alter status (update-status key (:data response)))
            (if (> @limit 0) (ref-set api-ready? true)))))

(defn get-likes-async [id]
  (let [uri (str "/shots/" id "/likes")
        key :likes
        response (api-request uri 1)
        f #(>!! likes-chan (map (comp :username :user) %))]
    (when (:next response)
      (get-rest uri f key 2))
    (f (:data response))
    (dosync (alter status (update-status key (:data response)))
            (if (> @limit 0) (ref-set api-ready? true)))))

(defn followers-consumer []
  (async/thread
    (loop []
      (let [follower (<!! followers-chan)]
        (async/thread-call #(get-shots-async follower)))
      (recur))))

(defn shots-consumer []
  (async/thread
    (loop []
      (let [shot (<!! shots-chan)]
        (async/thread-call #(get-likes-async shot))))
    (recur)))

(defn likes-consumer []
  (async/thread
    (loop [likes []]
      (let [[shot-likes ch] (async/alts!! [likes-chan (async/timeout 3000)])]
        (if-not (and (:likes @status) (not= ch likes-chan) @api-ready?
                     (= (count (concat shot-likes likes))
                        (:likes @status)))
          (if (not= ch likes-chan)
            (recur likes)
            (recur (concat likes shot-likes)))
          (let [likers (frequencies likes)]
            (>!! echo-chan ["done " @status @api-ready? (count (concat shot-likes likes))])
            (>!! done-chan (take 10 (into (sorted-map-by
                                           (fn [key1 key2] (< (likers key2) (likers key1))))
                                          likers))))
          )
          ))))

(defn -main
  [& [user]]
  (followers-consumer)
  (shots-consumer)
  (likes-consumer)
  (get-followers-async user)
  (loop []
    (let [[v ch] (async/alts!! [echo-chan done-chan])]
      (if (= ch echo-chan)
        (do (println v) (recur))
        (if (= ch done-chan)
          (dorun (map (fn [[user likes]] (println user likes))
                      v)))))))
