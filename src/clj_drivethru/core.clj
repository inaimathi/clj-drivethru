(ns clj-drivethru.core
  (:require [clojure-watch.core :as watch]
            [cheshire.core :as json]
            [trivial-openai.core :as ai]

            [clj-drivethru.sound :as snd]))

(def interaction (atom []))

(def audio-input (atom {}))
(defn update-interaction [filename transcription-result]
  (swap! audio-input))

;; 1. Have a background-thread waiting to record audio at a given threshold into a subdirectory of the working directory
;; 2. Have a background-process watching that directory
;; 3. Run `ai/transcription` on each file that gets put there. When a transcription contains text, add it to the `interaction` list as a `:user` message
;; 4. After a long enough delay (~2 seconds, but make it configurable) generate a response with `ai/chat`
;; 5. Maintain an order list
;; 6. Once an order is finalized, if the order is more than one item, ask the user to confirm, or if there's anything else
;; 7. When done, ask user to drive through, print order
;; 8. Log order to an order history directory

(defn process-order []
  (ai/chat
   [{:role :system :content "You are a fast-food drive-thru employee. Your job is to process incoming drive-thru orders."}
    {:role :user :content "Hello there. I'll take a large single with fries and a coke please."}]))


(def listener (atom nil))

(defn start! [dir]
  (let [thread (Thread. (fn [] (snd/record-ambient :dir dir)))
        stop-watch (watch/start-watch
                    [{:path dir
                      :event-types [:create :modify]
                      :callback (fn [event filename]
                                  (println "HAPPENING:" event filename)
                                  (if (= event :modify)
                                    ;;   It looks like `rec` creates the file, buffers audio
                                    ;; to memory and then writes the buffered audio stream
                                    ;; out to disk all at once on recording break.
                                    ;;   So, :create is kinda useless here, but we might want to
                                    ;; keep it around just for posterity;
                                    (println (ai/transcription filename))))}])]
    (.start thread)
    (reset! listener {:thread thread :watcher stop-watch})
    nil))

(defn stop! []
  ((:watcher @listener))
  (.interrupt (:thread @listener))
  (reset! listener nil)
  nil)




(def mnu
  (atom [{:role :system :content "You are a fast-food drive-through employee. You should be cheerful and solicitous. If your customer orders something that costs the same, or a little bit less than a combo, you should gently try to upsell them. You should start every interaction with 'Welcome to [Insert Restaurant]. May I take your order?' You should end every interactino by asking 'Will that be all?'. If the user replies in the affirmative, tell them the price of their order and instruct them to 'Drive through to the next window, please'."}
         {:role :system :content "The following is your restaurants' menu:"}
         {:role :system :content "Hot ‘n Juicy Cheeseburgers
Dave’s Hot ‘n Juicy 1/4 lb. Single with Cheese 		$4.19
Dave’s Hot ‘n Juicy 1/4 lb. Single with Cheese – Combo 		$6.19
Dave’s Hot ‘n Juicy 1/2 lb.. Double with Cheese 		$5.19
Dave’s Hot ‘n Juicy 1/2 lb.. Double with Cheese – Combo 		$7.19
Dave’s Hot ‘n Juicy 3/4 lb. Triple with Cheese 		$6.09
Dave’s Hot ‘n Juicy 3/4 lb. Triple with Cheese – Combo 		$8.09
Baconator 		$6.09
Baconator – Combo 		$8.09
Son of Baconator 		$4.69
Son of Baconator – Combo 		$6.69
Gouda Bacon Cheeseburger (Limited Time) 		$4.99
Gouda Bacon Cheeseburger – Combo (Limited Time) 		$6.99
Upgrade to Medium Combo 		$0.60
Upgrade to Large Combo 		$1.10"}
         {:role :system :content "Tender Juicy Chicken
Spicy Chicken 		$4.69
Spicy Chicken – Combo 		$6.69
Homestyle Chicken 		$4.69
Homestyle Chicken – Combo 		$6.69
Asiago Ranch Chicken Club 		$5.49
Asiago Ranch Chicken Club – Combo 		$7.49
Ultimate Chicken Grill 		$4.69
Ultimate Chicken Grill – Combo 		$6.69
10 Pc. Chicken Nuggets – Combo 		$5.99
Upgrade to Medium Combo 		$0.60
Upgrade to Large Combo 		$1.10"}
         {:role :system :content "Fresh Salads
Apple Pecan Chicken 	Half 	$4.69
Apple Pecan Chicken 	Full 	$6.69
Asian Cashew Chicken 	Half 	$4.69
Asian Cashew Chicken 	Full 	$6.69
BBQ Ranch Chicken 	Half 	$4.69
BBQ Ranch Chicken 	Full 	$6.69
Spicy Chicken Caesar 	Half 	$4.69
Spicy Chicken Caesar 	Full 	$6.69"}
         {:role :system :content "Sides
Natural-Cut Fries 	Small 	$1.69
Natural-Cut Fries 	Medium 	$1.99
Natural-Cut Fries 	Large 	$2.19
Cheese Fries 		$2.19
Bacon Fondue Fries (Limited Time) 		$1.99
Sour Cream & Chives Baked Potato 		$2.79
Rich & Meaty Chili 	Small 	$2.09
Rich & Meaty Chili 	Large 	$2.79
Caesar Side Salad 		$1.49
Garden Side Salad 		$1.49
Family Size Chili 		$9.99"}]))

(defn cht [history-atom input]
  (swap! history-atom #(conj % {:role :user :content input}))
  (let [res (ai/chat @history-atom)
        msg (get-in res ["choices" 0 "message" "content"])]
    (swap! history-atom #(conj % {:role :assistant :content msg}))
    msg))

(defn -json-chat [messages]
  (-> messages ai/chat (get-in ["choices" 0 "message" "content"]) json/decode))

(defn should-extract? [history]
  (-json-chat
   (conj history {:role :system :content "Does the above chat sequence contain a complete customer order? Answer in the form of a JSON value of type bool with no other commentary or formatting."})))

(defn extract [history]
  (-json-chat
   (conj history {:role :system :content "What did the user in the above chat sequence order? Answer in the form of a JSON value of type [String] with no other commentary or formatting."})))

;; (ai/chat
;;  [{:role :system :content "You are a fast-food drive-through employee. You should be cheerful and solicitous. If your customer orders something that costs the same, or a little bit less than a combo, you should gently try to upsell them. You should start every interaction with 'Welcome to [Insert Restaurant]. May I take your order?' You should end every interactino by asking 'Will that be all?'. If the user replies in the affirmative, tell them the price of their order and instruct them to 'Drive through to the next window, please'."}
;;   {:role :system :content "The following is your restaurants' menu:"}
;;   {:role :system :content "Hot ‘n Juicy Cheeseburgers
;; Dave’s Hot ‘n Juicy 1/4 lb. Single with Cheese 		$4.19
;; Dave’s Hot ‘n Juicy 1/4 lb. Single with Cheese – Combo 		$6.19
;; Dave’s Hot ‘n Juicy 1/2 lb.. Double with Cheese 		$5.19
;; Dave’s Hot ‘n Juicy 1/2 lb.. Double with Cheese – Combo 		$7.19
;; Dave’s Hot ‘n Juicy 3/4 lb. Triple with Cheese 		$6.09
;; Dave’s Hot ‘n Juicy 3/4 lb. Triple with Cheese – Combo 		$8.09
;; Baconator 		$6.09
;; Baconator – Combo 		$8.09
;; Son of Baconator 		$4.69
;; Son of Baconator – Combo 		$6.69
;; Gouda Bacon Cheeseburger (Limited Time) 		$4.99
;; Gouda Bacon Cheeseburger – Combo (Limited Time) 		$6.99
;; Upgrade to Medium Combo 		$0.60
;; Upgrade to Large Combo 		$1.10"}
;;   {:role :system :content "Tender Juicy Chicken
;; Spicy Chicken 		$4.69
;; Spicy Chicken – Combo 		$6.69
;; Homestyle Chicken 		$4.69
;; Homestyle Chicken – Combo 		$6.69
;; Asiago Ranch Chicken Club 		$5.49
;; Asiago Ranch Chicken Club – Combo 		$7.49
;; Ultimate Chicken Grill 		$4.69
;; Ultimate Chicken Grill – Combo 		$6.69
;; 10 Pc. Chicken Nuggets – Combo 		$5.99
;; Upgrade to Medium Combo 		$0.60
;; Upgrade to Large Combo 		$1.10"}
;;   {:role :system :content "Fresh Salads
;; Apple Pecan Chicken 	Half 	$4.69
;; Apple Pecan Chicken 	Full 	$6.69
;; Asian Cashew Chicken 	Half 	$4.69
;; Asian Cashew Chicken 	Full 	$6.69
;; BBQ Ranch Chicken 	Half 	$4.69
;; BBQ Ranch Chicken 	Full 	$6.69
;; Spicy Chicken Caesar 	Half 	$4.69
;; Spicy Chicken Caesar 	Full 	$6.69"}
;;   {:role :system :content "Sides
;; Natural-Cut Fries 	Small 	$1.69
;; Natural-Cut Fries 	Medium 	$1.99
;; Natural-Cut Fries 	Large 	$2.19
;; Cheese Fries 		$2.19
;; Bacon Fondue Fries (Limited Time) 		$1.99
;; Sour Cream & Chives Baked Potato 		$2.79
;; Rich & Meaty Chili 	Small 	$2.09
;; Rich & Meaty Chili 	Large 	$2.79
;; Caesar Side Salad 		$1.49
;; Garden Side Salad 		$1.49
;; Family Size Chili 		$9.99"}])

;; "




;; Right Price Right Size Menu
;; Jr. Cheeseburger Deluxe 		$1.89
;; Jr. Bacon Cheeseburger 		$1.99
;; Double Stack 		$2.09
;; 6 Pc. Regular or Spicy Chicken Nuggets 		$1.79
;; Chicken Go Wrap (Spicy or Grilled) 		$1.79
;; Caesar or Garden Side Salad 		$1.49
;; Chili 	Small 	$2.09
;; Jr. Cheeseburger 		$0.99
;; Crispy Chicken Sandwich 		$0.99
;; 4 Pc. Regular or Spicy Chicken Nuggets 		$0.99
;; Value Natural-Cut Fries 		$0.99
;; Value Soft Drink 		$0.99
;; Small Frosty 		$0.99

;; Hot Stuffed Baked Potatoes
;; Bacon & Cheese Baked Potato 		$2.89
;; Broccoli & Cheese Baked Potato 		$2.89

;; Sides
;; Natural-Cut Fries 	Small 	$1.69
;; Natural-Cut Fries 	Medium 	$1.99
;; Natural-Cut Fries 	Large 	$2.19
;; Cheese Fries 		$2.19
;; Bacon Fondue Fries (Limited Time) 		$1.99
;; Sour Cream & Chives Baked Potato 		$2.79
;; Rich & Meaty Chili 	Small 	$2.09
;; Rich & Meaty Chili 	Large 	$2.79
;; Caesar Side Salad 		$1.49
;; Garden Side Salad 		$1.49
;; Family Size Chili 		$9.99

;; 4 for $4 Meal
;; Meal (Limited Time) 	4 Pc. 	$4.00

;; Drinks
;; Soft Drink or Freshly Brewed Iced Tea 	Small 	$1.69
;; Soft Drink or Freshly Brewed Iced Tea 	Medium 	$1.89
;; Soft Drink or Freshly Brewed Iced Tea 	Large 	$2.19
;; Nestle Bottled Water 		$1.59
;; Trumoo Milk 1% Low-Fat (White or Chocolate) 		$1.29

;; Redhead Roasters
;; Hot Coffee (Regular or Decaf) 	Regular 	$0.99
;; Hot Coffee (Regular or Decaf) 	Large 	$1.49
;; Iced Coffee (Vanilla, Caramel, Mocha, or Skinny Vanilla) 	Small 	$1.79
;; Iced Coffee (Vanilla, Caramel, Mocha, or Skinny Vanilla) 	Medium 	$2.49
;; Premium Hot Tea 	Regular 	$0.99

;; Fresh-Baked Favorites
;; Espresso Chip Bar 		$1.39
;; Oatmeal Bar 		$1.39
;; Sugar Cookie 		$1.39
;; Chocolate Chunk Cookie 		$1.39
;; Double Chocolate Chip Cookie 		$1.39

;; Thick, Rich Frosty
;; Classic Frosty (Chocolate or Vanilla) 	Small 	$0.99
;; Classic Frosty (Chocolate or Vanilla) 	Medium 	$1.99
;; Classic Frosty (Chocolate or Vanilla) 	Large 	$2.29

;; Signature Beverages
;; All-Natural Lemonade 	Small 	$1.99
;; All-Natural Lemonade 	Medium 	$2.29
;; All-Natural Lemonade 	Large 	$2.69
;; Strawberry Lemonade 	Small 	$2.29
;; Strawberry Lemonade 	Medium 	$2.69
;; Strawberry Lemonade 	Large 	$3.19
;; Honest Tropical Green Tea 	Small 	$1.99
;; Honest Tropical Green Tea 	Medium 	$2.29
;; Honest Tropical Green Tea 	Large 	$2.69

;; Kid’s Meal
;; Grilled Chicken Wrap 		$3.79
;; 4 Pc. Chicken Nuggets 		$3.69
;; Cheeseburger 		$3.69
;; Hamburger 		$3.39"

(defonce stream (atom []))
(defn chat! [message]
  (swap! stream #(conj % {:role :user :content message}))
  nil)
(defn resp! []
  (let [res (ai/chat @stream)
        msg (get-in res ["choices" 0 "message" "content"])]
    (swap! stream #(conj % {:role :assistant :content msg}))
    (println msg)))
