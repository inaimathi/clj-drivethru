(ns clj-drivethru.sound
  (:require [clojure.string :as str]
            [clojure.edn :as edn]
            [clojure.java.shell :as shell]))

(def LAME "/opt/homebrew/bin/lame")
(def FFMPEG "/opt/homebrew/bin/ffmpeg")
(def REC "/opt/homebrew/bin/rec")
(def MPLAYER "/opt/homebrew/bin/mplayer")

(defn text->mp3 [text & {:keys [dir]}]
  (let [aiff (java.io.File/createTempFile "textReading" ".aiff")
        mp3 (java.io.File/createTempFile "textReading" ".mp3" (new java.io.File (or dir ".")))]
    (shell/sh "say" text "-o" (.getPath aiff))
    (shell/sh LAME "-m" "m" (.getPath aiff) (.getPath mp3))
    (.delete aiff)
    (.getPath mp3)))

(defn record-ambient [& {:keys [dir]}]
  (shell/sh
   REC "order.wav"
   "silence" "1" ".05" "1.3%" ;; wait until we hear activity above the threshold for more than 1/20th of a second then start recording
   "1" "3.0" "3.0%" ;; stop recording when audible activity falls to zero for 3.0 seconds
   "vad" ;; trim silence from the beginning of voice detection
   "gain" "-n" ;; normalize the gain
   ":" "newfile" ;; store result in a new file
   ;; ":" "restart" ;; restart listening process
   :dir dir))

(defn record-until-silence [& {:keys [dir filename silence-size] :or {filename "order.wav" silence-size 2.0}}]
  (let [res (shell/sh
             REC filename
             "vad" ;; trim silence from the beginning of voice detection
             "silence" "1" ".05" "1.3%" ;; wait until we hear activity above the threshold for more than 1/20th of a second
             "1" (str silence-size) "3.0%" ;; stop recording when audible activity falls to zero for silence-size seconds
             "gain" "-n" ;; normalize the gain
             :dir dir)]
    (if (= 0 (:exit res)) filename)))

(defn duration-of [file]
  (let [raw (shell/sh "bash" "-c" (str FFMPEG " -i " file " 2>&1 | grep Duration | awk '{print $2}' | tr -d ,"))
        res (-> raw :out str/trim-newline)]
    (if (= res "N/A")
      0
      (let [[h m s] (map edn/read-string (str/split res #":"))]
        (+ (* 60 60 h)
           (* 60 m)
           s)))))

(defn record [file & {:keys [duration] :or {duration 10}}]
  (shell/sh FFMPEG "-f" "avfoundation" "-i" ":0" "-t" (str duration) file)
  file)

(defn play [file & {:keys [speed] :or {speed 1.5}}]
  (shell/sh MPLAYER file "-speed" (str speed))
  nil)
