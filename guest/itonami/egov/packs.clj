(ns itonami.egov.packs
  "制度パックをコンパイル時に guest に埋め込む。guest は通信できない（CSP connect-src 'none'）ため。"
  (:require [clojure.edn :as edn]))

(defmacro bundled-packs []
  (mapv #(edn/read-string (slurp %)) ["seido/jp.edn" "seido/us.edn" "seido/se.edn"]))
