Readme file for building enclojure:
==============================================================================
Prerequisites:
------------------------------------------------------------------------------
;	1. git 
;	2. Apache Ant 1.7x or higher
;	3. Apache Ivy
	
Pulling all the source:
------------------------------------------------------------------------------
; At a command prompt:
git clone git://github.com/EricThorsen/enclojure.git

Building the plugin:
------------------------------------------------------------------------------
; At a command prompt:

cd enclojure

ant clean build-all

; The first build will pull down the deps.zip file located in the downloads 
; section of github and unpack it into the build-support/libs dir.  This 
; happens 1x.  There is a problem in how the dependancies are setup with 
; regards to Ivy where new source changes do not appear to trigger a refresh
; of the lib in the ivy-cache.  We are working to resolve this.  In the 
; meantime a 'clean' 'rebuild' ensures update code.  

Notes on Development:
------------------------------------------------------------------------------
; For convenience there are Netbeans projects for all the modules for the 
; enclojure plugin work.  If you go into the 'enclojure/src' directory you
; will see all the Netbeans projects and can open all of them from there.  Set
; the 'org.enclojure.ide.nb.editor' as the main project for debugging and
; rebuilds.


