(ns bundle-guest
  "target/guest/main.js を guest/index.html に埋め込み、dist/gyosei.html と digest を書く。
  host はこの 1 ファイルを revision と sha256 で固定して読み込む。"
  (:require [clojure.string :as str]))

(defn- sha256 [^bytes b]
  (let [d (.digest (java.security.MessageDigest/getInstance "SHA-256") b)]
    (apply str (map #(format "%02x" %) d))))

(defn -main [& _]
  (let [js (slurp "target/guest/main.js")
        _ (when (str/includes? js "</script") (throw (ex-info "script terminator in bundle" {})))
        html (str/replace (slurp "guest/index.html") "/*GUEST_JS*/" js)
        bytes (.getBytes html "UTF-8")
        digest (sha256 bytes)]
    (spit "dist/gyosei.html" html)
    (spit "dist/gyosei.json"
          (str "{\n  \"schema\": \"itonami.dapp.guest-artifact.v1\",\n  \"file\": \"dist/gyosei.html\",\n"
               "  \"bytes\": " (alength bytes) ",\n  \"sha256\": \"" digest "\"\n}\n"))
    (println "dist/gyosei.html" (alength bytes) "bytes sha256" digest)))
