(ns clj-drivethru.model
  (:require [trivial-openai.core :as ai]
            [clj-drivethru.sound :as snd]))

(def DATE-FORMAT (new java.text.SimpleDateFormat "yyyy-MM-dd'T'HH:mm:ss"))
(defn now [] (.format DATE-FORMAT (new java.util.Date)))

;;;;;;;;;; Interaction tracking
(declare PROMPT)
(def ORDER (atom nil))

(defn get-history [] (:interaction @ORDER))

(defn order-log! [event]
  (spit (str (:name @ORDER) "/events.edn") (str (assoc event :time (now)) "\n") :append true))

(defn new-order! []
  (let [n (now)
        name (str "order--" n)]
    (.mkdir (java.io.File. name))
    (reset! ORDER
            {:time n
             :name name
             :interaction PROMPT})))

(defn user-chat! [txt]
  (swap! ORDER update-in [:interaction] conj {:role :user :content txt}))

(defn robot-chat! [txt]
  (swap! ORDER update-in [:interaction] conj {:role :assistant :content txt}))

(defn robot-response! []
  (let [resp (ai/chat (get @ORDER :interaction))
        ai-txt (get-in resp ["choices" 0 "message" "content"])]
    (order-log! {:ai-response-id (get resp "id")})
    (order-log! {:ai-says ai-txt})
    (robot-chat! ai-txt)
    (let [ai-mp3 (snd/text->mp3 ai-txt :dir (:name @ORDER))]
      (order-log! {:ai-voiced ai-mp3})
      (snd/play ai-mp3))))

(defn wait-for-user-response! [& {:keys [attempts] :or {attempts 3}}]
  (when (> attempts 0)
    (let [fname (str (:name @ORDER) "/user-speaks" (count (get-history)) ".wav")
          file (snd/record-until-silence :filename fname)]
      (if file
        (do (order-log! {:user-voiced file})
            (let [message (ai/transcription file)]
              (when-let [txt (get message "text")]
                (order-log! {:ai-transcribed txt})
                (user-chat! txt))))
        (wait-for-user-response! :attempts (- attempts 1))))))

;;;;;;;;;; Transcription tracking
(def transcriptions
  (atom []))

(defn add-message! [filename]
  (println "ADDING -- " filename)
  (let [message (ai/transcription filename)]
    (println "  TRANSCRIBED -- " (str message))
    (if-let [txt (get message "text")]
      (swap! transcriptions conj {:text txt :time (now) :filename filename})
      (println "  ERROR -- fixme, log this case"))))

(defn get-messages []
  (map :text @transcriptions))

(defn clear! []
  (println "CLEARING -- fixme: log the previous transaction and seal it up here")
  (reset! transcriptions []))

;;;;;;;;;; Restaurant data
;;; FIXME - realistically, everything (other than possibly the prompt and even then, there's a decent argument for it)
;;          from this section should be in external edn or JSON config files
(def RESTAURANT "[Insert Restaurant]")
(def MENU
  "Hot ‘n Juicy Cheeseburgers
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
Upgrade to Large Combo 		$1.10

Tender Juicy Chicken
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
Upgrade to Large Combo 		$1.10

Fresh Salads
Apple Pecan Chicken 	Half 	$4.69
Apple Pecan Chicken 	Full 	$6.69
Asian Cashew Chicken 	Half 	$4.69
Asian Cashew Chicken 	Full 	$6.69
BBQ Ranch Chicken 	Half 	$4.69
BBQ Ranch Chicken 	Full 	$6.69
Spicy Chicken Caesar 	Half 	$4.69
Spicy Chicken Caesar 	Full 	$6.69

Sides
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
Family Size Chili 		$9.99

Right Price Right Size Menu
Jr. Cheeseburger Deluxe 		$1.89
Jr. Bacon Cheeseburger 		$1.99
Double Stack 		$2.09
6 Pc. Regular or Spicy Chicken Nuggets 		$1.79
Chicken Go Wrap (Spicy or Grilled) 		$1.79
Caesar or Garden Side Salad 		$1.49
Chili 	Small 	$2.09
Jr. Cheeseburger 		$0.99
Crispy Chicken Sandwich 		$0.99
4 Pc. Regular or Spicy Chicken Nuggets 		$0.99
Value Natural-Cut Fries 		$0.99
Value Soft Drink 		$0.99
Small Frosty 		$0.99

Hot Stuffed Baked Potatoes
Bacon & Cheese Baked Potato 		$2.89
Broccoli & Cheese Baked Potato 		$2.89

Sides
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
Family Size Chili 		$9.99

4 for $4 Meal
Meal (Limited Time) 	4 Pc. 	$4.00

Drinks
Soft Drink or Freshly Brewed Iced Tea 	Small 	$1.69
Soft Drink or Freshly Brewed Iced Tea 	Medium 	$1.89
Soft Drink or Freshly Brewed Iced Tea 	Large 	$2.19
Nestle Bottled Water 		$1.59
Trumoo Milk 1% Low-Fat (White or Chocolate) 		$1.29

Redhead Roasters
Hot Coffee (Regular or Decaf) 	Regular 	$0.99
Hot Coffee (Regular or Decaf) 	Large 	$1.49
Iced Coffee (Vanilla, Caramel, Mocha, or Skinny Vanilla) 	Small 	$1.79
Iced Coffee (Vanilla, Caramel, Mocha, or Skinny Vanilla) 	Medium 	$2.49
Premium Hot Tea 	Regular 	$0.99

Fresh-Baked Favorites
Espresso Chip Bar 		$1.39
Oatmeal Bar 		$1.39
Sugar Cookie 		$1.39
Chocolate Chunk Cookie 		$1.39
Double Chocolate Chip Cookie 		$1.39

Thick, Rich Frosty
Classic Frosty (Chocolate or Vanilla) 	Small 	$0.99
Classic Frosty (Chocolate or Vanilla) 	Medium 	$1.99
Classic Frosty (Chocolate or Vanilla) 	Large 	$2.29

Signature Beverages
All-Natural Lemonade 	Small 	$1.99
All-Natural Lemonade 	Medium 	$2.29
All-Natural Lemonade 	Large 	$2.69
Strawberry Lemonade 	Small 	$2.29
Strawberry Lemonade 	Medium 	$2.69
Strawberry Lemonade 	Large 	$3.19
Honest Tropical Green Tea 	Small 	$1.99
Honest Tropical Green Tea 	Medium 	$2.29
Honest Tropical Green Tea 	Large 	$2.69

Kid’s Meal
Grilled Chicken Wrap 		$3.79
4 Pc. Chicken Nuggets 		$3.69
Cheeseburger 		$3.69
Hamburger 		$3.39")


(def PROMPT
  [{:role :system :content
    (format "You are a fast-food drive-through employee. You should be cheerful and solicitous. If your customer orders something that costs the same, or a little bit less than a combo, you should gently try to upsell them. You should start every interaction with 'Welcome to %s. May I take your order?' You should end every interactino by asking 'Will that be all?'. If the user replies in the affirmative, tell them the price of their order and instruct them to 'Drive through to the next window, please'."
            RESTAURANT)}
   {:role :system :content "If the user orders something not on the menu, try to clarify what the closest amount on the menu it."}
   {:role :system :content "The following is your restaurants' menu:"}
   {:role :system :content MENU}])
