(comment
;*******************************************************************************
;*    Copyright (c) ThorTech, L.L.C.. All rights reserved.
;*    The use and distribution terms for this software are covered by the
;*    GNU General Public License, version 2
;*    (http://www.gnu.org/licenses/old-licenses/gpl-2.0.html) with classpath
;*    exception (http://www.gnu.org/software/classpath/license.html)
;*    which can be found in the file GPL-2.0+ClasspathException.txt at the root
;*    of this distribution.
;*    By using this software in any fashion, you are agreeing to be bound by
;*    the terms of this license.
;*    You must not remove this notice, or any other, from this software.
;*******************************************************************************
;*    Author: Eric Thorsen
;*******************************************************************************
)

(ns org.enclojure.ide.nb.classpaths.executors
  (:use org.enclojure.commons.meta-utils
    org.enclojure.commons.logging)  
  (:import 
    (java.util.logging Level)
    (java.util.concurrent LinkedBlockingQueue CountDownLatch
      Executors ExecutorService TimeUnit BlockingQueue ThreadPoolExecutor)))

(defrt #^{:private true} log (get-ns-logfn))

(defmulti thread-pool-executor
  [& args]
  (vec (map class args)))

(defmethod thread-pool-executor
  [Integer Integer Integer TimeUnit BlockingQueue]
  [corePoolSize maximumPoolSize keepAliveTime unit workQueue]
  (proxy [ThreadPoolExecutor] [corePoolSize maximumPoolSize keepAliveTime unit workQueue]
    (afterExecute [runnable throwable])
    (beforeExecute [thread runnable r])))
  

;          Creates a new ThreadPoolExecutor with the given initial parameters and default thread factory and handler.
;ThreadPoolExecutor(int corePoolSize, int maximumPoolSize, long keepAliveTime, TimeUnit unit, BlockingQueue<Runnable> workQueue, RejectedExecutionHandler handler)
;          Creates a new ThreadPoolExecutor with the given initial parameters.
;ThreadPoolExecutor(int corePoolSize, int maximumPoolSize, long keepAliveTime, TimeUnit unit, BlockingQueue<Runnable> workQueue, ThreadFactory threadFactory)
;          Creates a new ThreadPoolExecutor with the given initial parameters.
;ThreadPoolExecutor(int corePoolSize, int maximumPoolSize, long keepAliveTime, TimeUnit unit, BlockingQueue<Runnable> workQueue, ThreadFactory threadFactory, RejectedExecutionHandler handler)