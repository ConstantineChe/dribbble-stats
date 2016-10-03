(ns dribble-stats.url-match-test
  (:require [dribble-stats.urlmatch :refer [recognize]]
            [midje.sweet :refer :all])
  (:import [dribble_stats.urlmatch Pattern]))

(def dribbble (Pattern.
               "host(dribbble.com); path(shots/?id); queryparam(offset=?offset);"))

(recognize (Pattern. "host(twitter.com); path(?user/status/?id);")
 "https://dribbble.com/shots/1905065-Travel-Icons-pack?list=users&offset=1")

(facts "URL matcher"
       (fact "Matcher can match path from url."
             (let [twitter (Pattern. "host(twitter.com); path(?user/status/?id);")]
               (recognize twitter "http://twitter.com/bradfitz/status/562360748727611392")
               => [[:user "bradfitz"] [:id "562360748727611392"]]))

       (let [dribbble (Pattern.
                       "host(dribbble.com); path(shots/?id); queryparam(offset=?offset);")]
         (fact "Matcher can match query params."
               (recognize
                dribbble
                "https://dribbble.com/shots/1905065-Travel-Icons-pack?list=users&offset=1")
               => [[:id "1905065-Travel-Icons-pack"] [:offset "1"]])

         (fact "Matcher will return nil in case of host missmatch."
               (recognize dribbble
                          "https://twitter.com/shots/1905065-Travel-Icons-pack?list=users&offset=1")
               => nil)
         (fact "Matcher will return nil in case of absence of query param."
               (recognize dribbble
                          "https://dribbble.com/shots/1905065-Travel-Icons-pack?list=users")
               => nil))

       (let [dribbble2 (Pattern. (str "host(dribbble.com); path(?user/shots/?id); "
                                      "queryparam(offset=?offset); queryparam(list=?type);"))]
            (fact "URL match pattern can have multiple queryparam clauses."
                  (recognize
                   dribbble2
                   "https://dribbble.com/mek/shots/1905065-Travel-Icons-pack?list=users&offset=1")
                  => [[:user "mek"] [:id "1905065-Travel-Icons-pack"] [:offset "1"] [:type "users"]])))
