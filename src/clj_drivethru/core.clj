(ns clj-drivethru.core
  (:require [clojure.string :as str]
            [clojure-watch.core :as watch]
            [cheshire.core :as json]
            [trivial-openai.core :as ai]

            [clj-drivethru.sound :as snd]
            [clj-drivethru.model :as model]))


(defn should-extract? [history]
  (-> (conj history {:role :system :content "Given the above chat log: Is the users' order complete? Please answer with one word; yes or no"})
      ai/chat
      (get-in ["choices" 0 "message" "content"])
      str/lower-case
      (= "yes")))

(defn extract [history]
  (-> (conj history {:role :system :content "Given the above chat log: What is the users final order, accounting for any upgrades and cancellations?"})
      ai/chat
      (get-in ["choices" 0 "message" "content"])))

(defn take-order! []
  (model/new-order!)
  (let [f (fn interaction-loop [resp]
            (if (should-extract? (model/get-history))
              (extract (model/get-history))
              (do (resp)
                  (interaction-loop
                   (if (= resp model/robot-response!)
                     model/wait-for-user-response!
                     model/robot-response!)))))]
    (f model/robot-response!)))
