(comment
;    Copyright (c) ThorTech, L.L.C.. All rights reserved.
;    The use and distribution terms for this software are covered by the
;    Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;    which can be found in the file epl-v10.html at the root of this distribution.
;    By using this software in any fashion, you are agreeing to be bound by
;    the terms of this license.
;    You must not remove this notice, or any other, from this software.
;
;    Author: Eric Thorsen
)

(ns org.enclojure.ide.navigator.CljClassVisitor
  (:gen-class
   :implements [org.enclojure.ide.asm.ClassVisitor])  
  (:import (java.util.logging Level)
    (org.enclojure.ide.asm ClassVisitor)
    (java.util.concurrent LinkedBlockingQueue CountDownLatch)))

(defstruct class-meta :version :access  :name  :signature :superName :interfaces :fields :methods)
(defstruct field-meta :access :name :desc :signature :value)
(defstruct method-meta :access :name :desc :signature :exceptions)

(def classes-q (LinkedBlockingQueue.))
(def methods-q (LinkedBlockingQueue.))
(def fields-q (LinkedBlockingQueue.))

(defn clear-queues []
  (doall (map #(.clear %1) [classes-q methods-q fields-q])))

(def data (ref {}))

(defn -visit [this version access, name, signature, superName, interfaces]
    (let [array (make-array java.lang.Object 4)]
    (aset array 0 access)
    (aset array 1 name)
    (aset array 2 signature)
    (aset array 3 superName)
  (.put classes-q array)))

     
(defn -visitAnnotation [this desc, visible]
    )
    
(defn -visitAttribute [this attr])

(defn -visitEnd [this ])

(defn -visitField [this access, name, desc,signature, value]
  (println "fields " (interpose ", " [access, name, desc, signature,value])))
;    (let [array (make-array java.lang.Object 4)]
;    (aset array 0 access)
;    (aset array 1 name)
;    (aset array 2 signature)
;    (aset array 3 value)
;  (.put fields-q array)))

(defn -visitInnerClass [this name, outerName, innerName, access])

(defn -visitMethod [this access, name, desc, signature, exceptions]
  (println "method " (interpose ", " [access, name, desc, signature, exceptions])))
;    (let [array (make-array java.lang.Object 4)]
;        (aset array 0 access)
;        (aset array 1 name)
;        (aset array 2 signature)
;        (aset array 3 exceptions)
;    (.put methods-q array)))

(defn -visitOuterClass [this owner, name, desc])

(defn -visitSource [this source, debug])

