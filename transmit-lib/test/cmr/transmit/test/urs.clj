(ns cmr.transmit.test.urs
  "This tests some of the helper functions in the URS namespace"
  (:require [clojure.test :refer :all]
            [cmr.transmit.urs :as urs]))

(def sample-successful-login-response
  "<LoginResponse>
   <reason/>
   <success>true</success>
   <user>...</user>
  </LoginResponse>")

(def sample-unsuccessful-login-response
  "<LoginResponse>
  <reason>Invalid username or password</reason>
  <success>false</success>
  </LoginResponse>")

(deftest assert-login-response-successful-test
  (testing "Unsuccessful login"
   (is (thrown-with-msg?
        clojure.lang.ExceptionInfo
        #"Invalid username or password"
        (#'urs/assert-login-response-successful 200 sample-unsuccessful-login-response))))
  (testing "Successful login"
   (#'urs/assert-login-response-successful 200 sample-successful-login-response))
  (testing "Unexpected response code"
    (is (thrown-with-msg?
         Exception
         #"Unexpected status code \[401\].*"
         (#'urs/assert-login-response-successful 401 sample-unsuccessful-login-response)))))


