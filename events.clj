(require '[clj-time.core :as t])

[{:regex #"(?i)account" :time (t/date-time 2015 11 18 ) :desc "accounts - deadline for our accounts"}
 {:regex #"(?i)AGM" :time (t/date-time 2016 4 01) :desc "AGM - our next AGM" }
 {:regex #"(?i)certs" :time (t/date-time 2016 4 02) :desc "certs - our SSL certificates expire"}
 {:regex #"(?i)anniv" :time (t/date-time 2016 9 13) :desc "anniversary - we roll over to another year older"}
 {:regex #"(?i)hallow" :time (t/date-time 2015 10 31) :desc "halloween - all hallows eve"}]