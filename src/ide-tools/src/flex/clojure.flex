/*
(comment
*
*    Copyright (c) ThorTech, L.L.C.. All rights reserved.
*    The use and distribution terms for this software are covered by the
*    Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
*    which can be found in the file epl-v10.html at the root of this distribution.
*    By using this software in any fashion, you are agreeing to be bound by
*    the terms of this license.
*    You must not remove this notice, or any other, from this software.
*
*    Author: Eric Thorsen
)
 */
package org.enclojure.ide_tools;

import java.util.*;
import java.io.CharArrayReader;
import clojure.lang.IPersistentMap;
import clojure.lang.RT;
import clojure.lang.IFn;
import clojure.lang.Var;
import clojure.lang.Keyword;
import clojure.lang.LispReader;
import clojure.lang.Symbol;
import java.util.regex.Pattern;
import java_cup.runtime.*;
import flex.ClojureSym;

%%

%class _ClojureLexer
%cup
%char
%line
%column
%unicode
%public
%debug
%function next_token
%type ClojureSym

%eof{ return;
%eof}

////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
/////////////////////// User code //////////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

%{

public ClojureSym symbol(IPersistentMap tokenType,Object data)
{
    ClojureSym.create(tokenType,yyline, yycolumn,yychar,data);
}

public ClojureSym symbol(IPersistentMap tokenType)
{
    ClojureSym.create(tokenType,yyline, yycolumn,yychar);
}

//From LispReader
static final clojure.lang.Symbol QUOTE = Symbol.create("quote");
static final clojure.lang.Symbol THE_VAR = Symbol.create("var");
static clojure.lang.Symbol UNQUOTE = Symbol.create("clojure.core", "unquote");
static clojure.lang.Symbol UNQUOTE_SPLICING = Symbol.create("clojure.core", "unquote-splicing");
static clojure.lang.Symbol CONCAT = Symbol.create("clojure.core", "concat");
static clojure.lang.Symbol SEQ = Symbol.create("clojure.core", "seq");
static clojure.lang.Symbol LIST = Symbol.create("clojure.core", "list");
static clojure.lang.Symbol APPLY = Symbol.create("clojure.core", "apply");
static clojure.lang.Symbol HASHMAP = Symbol.create("clojure.core", "hash-map");
static clojure.lang.Symbol HASHSET = Symbol.create("clojure.core", "hash-set");
static clojure.lang.Symbol VECTOR = Symbol.create("clojure.core", "vector");
static clojure.lang.Symbol WITH_META = Symbol.create("clojure.core", "with-meta");
static clojure.lang.Symbol META = Symbol.create("clojure.core", "meta");
static clojure.lang.Symbol DEREF = Symbol.create("clojure.core", "deref");

static IFn[] macros = new IFn[256];
static IFn[] dispatchMacros = new IFn[256];

static Pattern symbolPat = Pattern.compile("[:]?([\\D&&[^/]].*/)?([\\D&&[^/]][^/]*)");
static Pattern intPat =
		Pattern.compile(
				"([-+]?)(?:(0)|([1-9][0-9]*)|0[xX]([0-9A-Fa-f]+)|0([0-7]+)|([1-9][0-9]?)[rR]([0-9A-Za-z]+)|0[0-9]+)");
static Pattern ratioPat = Pattern.compile("([-+]?[0-9]+)/([0-9]+)");
static Pattern floatPat = Pattern.compile("([-+]?[0-9]+(\\.[0-9]*)?([eE][-+]?[0-9]+)?)(M)?");
static final clojure.lang.Symbol SLASH = Symbol.create("/");
static final clojure.lang.Symbol CLOJURE_SLASH = Symbol.create("clojure.core","/");


//symbol->gensymbol
static clojure.lang.Var GENSYM_ENV = clojure.lang.Var.create(null);
//sorted-map num->gensymbol
static clojure.lang.Var ARG_ENV = clojure.lang.Var.create(null);

/*
    static
	{
	macros['"'] = new LispReader.StringReader();
	macros[';'] = new LispReader.CommentReader();
	macros['\''] = new LispReader.WrappingReader(QUOTE);
	macros['@'] = new LispReader.WrappingReader(DEREF);//new DerefReader();
	macros['^'] = new LispReader.WrappingReader(META);
	macros['`'] = new LispReader.SyntaxQuoteReader();
	macros['~'] = new LispReader.UnquoteReader();
	macros['('] = new LispReader.ListReader();
	macros[')'] = new LispReader.UnmatchedDelimiterReader();
	macros['['] = new LispReader.VectorReader();
	macros[']'] = new LispReader.UnmatchedDelimiterReader();
	macros['{'] = new LispReader.MapReader();
	macros['}'] = new LispReader.UnmatchedDelimiterReader();
	macros['\\'] = new LispReader.CharacterReader();
	macros['%'] = new LispReader.ArgReader();
	macros['#'] = new LispReader.DispatchReader();


	dispatchMacros['^'] = new LispReader.MetaReader();
	dispatchMacros['\''] = new LispReader.VarReader();
	dispatchMacros['"'] = new LispReader.RegexReader();
	dispatchMacros['('] = new LispReader.FnReader();
	dispatchMacros['{'] = new LispReader.SetReader();
	dispatchMacros['='] = new LispReader.EvalReader();
	dispatchMacros['!'] = new LispReader.CommentReader();
	dispatchMacros['<'] = new LispReader.UnreadableReader();
	dispatchMacros['_'] = new LispReader.DiscardReader();
	}
*/
	final static IPersistentMap cSTRING_LITERAL = (IPersistentMap)RT.var("org.enclojure.ide.tokens","cSTRING-LITERAL").get();
	final static IPersistentMap cWRONG_STRING_LITERAL = (IPersistentMap)RT.var("org.enclojure.ide.tokens","cWRONG-STRING-LITERAL").get();
	final static IPersistentMap cLONG_LITERAL = (IPersistentMap)RT.var("org.enclojure.ide.tokens","cLONG-LITERAL").get();
	final static IPersistentMap cWHITESPACE = (IPersistentMap)RT.var("org.enclojure.ide.tokens","cWHITESPACE").get();
	final static IPersistentMap cLINE_COMMENT = (IPersistentMap)RT.var("org.enclojure.ide.tokens","cLINE-COMMENT").get();
	final static IPersistentMap cCOLON_SYMBOL = (IPersistentMap)RT.var("org.enclojure.ide.tokens","cCOLON-SYMBOL").get();
	final static IPersistentMap cSHARPUP = (IPersistentMap)RT.var("org.enclojure.ide.tokens","cSHARPUP").get();
	final static IPersistentMap cRIGHT_SQUARE = (IPersistentMap)RT.var("org.enclojure.ide.tokens","cRIGHT-SQUARE").get();
	final static IPersistentMap get_java_def = (IPersistentMap)RT.var("org.enclojure.ide.tokens","get-java-def").get();
	final static IPersistentMap cTRUE = (IPersistentMap)RT.var("org.enclojure.ide.tokens","cTRUE").get();
	final static IPersistentMap _token_meta_ = (IPersistentMap)RT.var("org.enclojure.ide.tokens","-token-meta-").get();
	final static IPersistentMap cTILDAAT = (IPersistentMap)RT.var("org.enclojure.ide.tokens","cTILDAAT").get();
	final static IPersistentMap cEOF = (IPersistentMap)RT.var("org.enclojure.ide.tokens","cEOF").get();
	final static IPersistentMap symATOM = (IPersistentMap)RT.var("org.enclojure.ide.tokens","symATOM").get();
	final static IPersistentMap cRIGHT_PAREN = (IPersistentMap)RT.var("org.enclojure.ide.tokens","cRIGHT-PAREN").get();
	final static IPersistentMap cBIG_DECIMAL_LITERAL = (IPersistentMap)RT.var("org.enclojure.ide.tokens","cBIG-DECIMAL-LITERAL").get();
	final static IPersistentMap cNIL = (IPersistentMap)RT.var("org.enclojure.ide.tokens","cNIL").get();
	final static IPersistentMap cWHITESPACE_SET = (IPersistentMap)RT.var("org.enclojure.ide.tokens","cWHITESPACE-SET").get();
	final static IPersistentMap symIMPLICIT_ARG = (IPersistentMap)RT.var("org.enclojure.ide.tokens","symIMPLICIT-ARG").get();
	final static IPersistentMap cFLOAT_LITERAL = (IPersistentMap)RT.var("org.enclojure.ide.tokens","cFLOAT-LITERAL").get();
	final static IPersistentMap cCHAR_LITERAL = (IPersistentMap)RT.var("org.enclojure.ide.tokens","cCHAR-LITERAL").get();
	final static IPersistentMap cLEFT_PAREN = (IPersistentMap)RT.var("org.enclojure.ide.tokens","cLEFT-PAREN").get();
	final static IPersistentMap cSHARP = (IPersistentMap)RT.var("org.enclojure.ide.tokens","cSHARP").get();
	final static IPersistentMap cDOUBLE_LITERAL = (IPersistentMap)RT.var("org.enclojure.ide.tokens","cDOUBLE-LITERAL").get();
	final static IPersistentMap cBIG_INT_LITERAL = (IPersistentMap)RT.var("org.enclojure.ide.tokens","cBIG-INT-LITERAL").get();
	final static IPersistentMap cLEFT_SQUARE = (IPersistentMap)RT.var("org.enclojure.ide.tokens","cLEFT-SQUARE").get();
	final static IPersistentMap cLEFT_CURLY = (IPersistentMap)RT.var("org.enclojure.ide.tokens","cLEFT-CURLY").get();
	final static IPersistentMap cTILDA = (IPersistentMap)RT.var("org.enclojure.ide.tokens","cTILDA").get();
	final static IPersistentMap cREADABLE_TEXT = (IPersistentMap)RT.var("org.enclojure.ide.tokens","cREADABLE-TEXT").get();
	final static IPersistentMap symNS_SEP = (IPersistentMap)RT.var("org.enclojure.ide.tokens","symNS-SEP").get();
	final static IPersistentMap make_token = (IPersistentMap)RT.var("org.enclojure.ide.tokens","make-token").get();
	final static IPersistentMap cQUOTE = (IPersistentMap)RT.var("org.enclojure.ide.tokens","cQUOTE").get();
	final static IPersistentMap cUP = (IPersistentMap)RT.var("org.enclojure.ide.tokens","cUP").get();
	final static IPersistentMap cRIGHT_CURLY = (IPersistentMap)RT.var("org.enclojure.ide.tokens","cRIGHT-CURLY").get();
	final static IPersistentMap cCOMMA = (IPersistentMap)RT.var("org.enclojure.ide.tokens","cCOMMA").get();
	final static IPersistentMap symS = (IPersistentMap)RT.var("org.enclojure.ide.tokens","symS").get();
	final static IPersistentMap cAT = (IPersistentMap)RT.var("org.enclojure.ide.tokens","cAT").get();
	final static IPersistentMap cCOMMENTS = (IPersistentMap)RT.var("org.enclojure.ide.tokens","cCOMMENTS").get();
	final static IPersistentMap cFALSE = (IPersistentMap)RT.var("org.enclojure.ide.tokens","cFALSE").get();
	final static IPersistentMap make_token_set = (IPersistentMap)RT.var("org.enclojure.ide.tokens","make-token-set").get();
	final static IPersistentMap cRATIO = (IPersistentMap)RT.var("org.enclojure.ide.tokens","cRATIO").get();
	final static IPersistentMap symDOT = (IPersistentMap)RT.var("org.enclojure.ide.tokens","symDOT").get();
	final static IPersistentMap _TOKEN_TYPES_MAP_ = (IPersistentMap)RT.var("org.enclojure.ide.tokens","-TOKEN-TYPES-MAP-").get();
	final static IPersistentMap _TOKEN_TYPES_BY_ID_ = (IPersistentMap)RT.var("org.enclojure.ide.tokens","-TOKEN-TYPES-BY-ID-").get();
	final static IPersistentMap cSEPARATORS = (IPersistentMap)RT.var("org.enclojure.ide.tokens","cSEPARATORS").get();
	final static IPersistentMap cINTEGER_LITERAL = (IPersistentMap)RT.var("org.enclojure.ide.tokens","cINTEGER-LITERAL").get();
	final static IPersistentMap cIDENTIFIERS = (IPersistentMap)RT.var("org.enclojure.ide.tokens","cIDENTIFIERS").get();
	final static IPersistentMap cLITERALS = (IPersistentMap)RT.var("org.enclojure.ide.tokens","cLITERALS").get();
	final static IPersistentMap cBOOLEAN_LITERAL = (IPersistentMap)RT.var("org.enclojure.ide.tokens","cBOOLEAN-LITERAL").get();
	final static IPersistentMap cBAD_CHARACTER = (IPersistentMap)RT.var("org.enclojure.ide.tokens","cBAD-CHARACTER").get();
	final static IPersistentMap cEOL = (IPersistentMap)RT.var("org.enclojure.ide.tokens","cEOL").get();
	final static IPersistentMap cBACKQUOTE = (IPersistentMap)RT.var("org.enclojure.ide.tokens","cBACKQUOTE").get();
	final static IPersistentMap cSTRINGS = (IPersistentMap)RT.var("org.enclojure.ide.tokens","cSTRINGS").get();
	final static IPersistentMap cATOMS = (IPersistentMap)RT.var("org.enclojure.ide.tokens","cATOMS").get();

%}

////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
////////// NewLines and spaces /////////////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

mNL = \r | \n | \r\n                                    // NewLines
mWS = " " | \t | \f | {mNL}                       // Whitespaces
mCOMMA = ","

////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
/////////////////////      integers and floats     /////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

mHEX_DIGIT = [0-9A-Fa-f]
mDIGIT = [0-9]
mBIG_SUFFIX = g | G
mFLOAT_SUFFIX = f | F
mLONG_SUFFIX = l | L
mINT_SUFFIX = i | I
mDOUBLE_SUFFIX = d | D
mEXPONENT = (e | E)("+" | "-")?([0-9])+

mNUM_INT_PART =  0
 ( (x | X){mHEX_DIGIT}+
   | {mDIGIT}+
   | ([0-7])+
 )?
 | {mDIGIT}+

// Integer
mNUM_INT = {mNUM_INT_PART} {mINT_SUFFIX}?

// Long
mNUM_LONG = {mNUM_INT_PART} {mLONG_SUFFIX}

// BigInteger
mNUM_BIG_INT = {mNUM_INT_PART} {mBIG_SUFFIX}

//Float
mNUM_FLOAT = {mNUM_INT_PART} ( ("." {mDIGIT}+ {mEXPONENT}? {mFLOAT_SUFFIX})
 | {mFLOAT_SUFFIX}
 | {mEXPONENT} {mFLOAT_SUFFIX} )

// Double
mNUM_DOUBLE = {mNUM_INT_PART} ( ("." {mDIGIT}+ {mEXPONENT}? {mDOUBLE_SUFFIX})
 | {mDOUBLE_SUFFIX}
 | {mEXPONENT} {mDOUBLE_SUFFIX})

// BigDecimal
mNUM_BIG_DECIMAL = {mNUM_INT_PART} ( ("." {mDIGIT}+ {mEXPONENT}? {mBIG_SUFFIX}?)
 | {mEXPONENT} {mBIG_SUFFIX}? )

//Ratios
mRATIO = {mNUM_INT_PART} "/" {mNUM_INT_PART}

////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
////////// Parens, Squares, Curleys, Quotes /////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

mLP = "("
mRP = ")"
mLS = "["
mRS = "]"
mLC = "{"
mRC = "}"

mQUOTE = "'"
mBACKQUOTE = "`"
mSHARP = "#"
mSHARPUP = {mSHARP} {mUP}
mUP = "^"
mIMPLICIT_ARG = "%" | "%"{mDIGIT}+ | "%""&"
mTILDA = "~"
mAT = "@"
mTILDAAT = {mTILDA} {mAT}

////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
////////// Strings /////////////////////////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

mONE_NL = \r | \n | \r\n
mHEX_DIGIT = [0-9A-Fa-f]

mCHAR = \\ [^" "\r\n]
    | \\ [:jletter:]+

mSTRING_ESC = \\ n | \\ r | \\ t | \\ b | \\ f | "\\" "\\" | \\ "$" | \\ \" | \\ \'
    | "\\""u"{mHEX_DIGIT}{4}
    | "\\" [0..3] ([0..7] ([0..7])?)?
    | "\\" [4..7] ([0..7])?
    | "\\" {mONE_NL}
    | {mCHAR}


mSTRING_CONTENT = ({mSTRING_ESC}|[^\\\"])*
mSTRING = \"\" | \" ([^\\\"] | {mSTRING_ESC})? {mSTRING_CONTENT} \"
mWRONG_STRING = \" ([^\\\"] | {mSTRING_ESC})? {mSTRING_CONTENT}


////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
////////// Comments ////////////////////////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

mLINE_COMMENT = ";" [^\r\n]*

////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
/////////////////////      identifiers      ////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

mLETTER = [A-Z] | [a-z]
mSLASH_LETTER = \\ ({mLETTER} | .)

mOTHER = "_" | "-" | "*" | "." | "+" | "=" | "&" | "<" | ">" | "$" | "/" | "?" | "!"
mNoDigit = ({mLETTER} | {mOTHER})

mOTHER_REDUCED = "_" | "-" | "*" | "+" | "=" | "&" | "<" | ">" | "$" | "?" | "!"
mNoDigit1 = ({mLETTER} | {mOTHER_REDUCED})

mIDENT = {mNoDigit} ({mNoDigit} | {mDIGIT})* "#"?
mKEY = ":" (":")? ({mIDENT} ":")* {mIDENT}

////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
/////////////////////      predefined      ////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

mNIL = "nil"
mTRUE = "true"
mFALSE = "false"

////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
////////////////////  states ///////////////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

%xstate SYMBOL

%%
<SYMBOL> {
  "."                                       {  return symbol(symDOT); }
  "/"                                       {  return symbol(symNS_SEP); }
  ({mNoDigit1} | {mDIGIT} | ":")+           {  return symbol(symATOM); }
  (({mNoDigit1} | {mDIGIT} | ":")+)? "#"    {  yybegin(YYINITIAL); return symbol(symATOM); }
  [^]                                       {  yypushback(yytext().length()); yybegin(YYINITIAL); }
}

<YYINITIAL>{

  {mLINE_COMMENT}                           {  return symbol(cLINE_COMMENT); }
  
  {mWS}+                                    {  return symbol(cWHITESPACE); }
  {mCOMMA}                                  {  return symbol(cCOMMA); }

  {mSTRING}                                 {  return symbol(cSTRING_LITERAL); }
  {mWRONG_STRING }                          {  return symbol(cWRONG_STRING_LITERAL); }

  {mCHAR}                                   {  return symbol(cCHAR_LITERAL); }
  {mNIL}                                    {  return symbol(cNIL); }
  {mTRUE}                                   {  return symbol(cTRUE); }
  {mFALSE}                                  {  return symbol(cFALSE); }

  {mNUM_INT}                                {  return symbol(cINTEGER_LITERAL); }
  {mNUM_LONG}                               {  return symbol(cLONG_LITERAL); }
  {mNUM_BIG_INT}                            {  return symbol(cBIG_INT_LITERAL); }
  {mNUM_FLOAT}                              {  return symbol(cFLOAT_LITERAL); }
  {mNUM_DOUBLE}                             {  return symbol(cDOUBLE_LITERAL); }
  {mNUM_BIG_DECIMAL}                        {  return symbol(cBIG_DECIMAL_LITERAL); }
  {mRATIO}                                  {  return symbol(cRATIO); }

  // Reserved symbols
  "/"                                       {  return symbol(symATOM); }
  "."{mIDENT} | {mIDENT}"."                 {  return symbol(symATOM); }
  {mIDENT}                                  {  yypushback(yytext().length()); yybegin(SYMBOL); }
  {mKEY}                                    {  return symbol(cCOLON_SYMBOL); }


  {mQUOTE}                                  {  return symbol(cQUOTE); }
  {mBACKQUOTE}                              {  return symbol(cBACKQUOTE); }
  {mSHARPUP}                                {  return symbol(cSHARPUP); }
  {mSHARP}                                  {  return symbol(cSHARP); }
  {mUP}                                     {  return symbol(cUP); }
  {mIMPLICIT_ARG}                           {  return symbol(symIMPLICIT_ARG); }
  {mTILDA}                                  {  return symbol(cTILDA); }
  {mAT}                                     {  return symbol(cAT); }
  {mTILDAAT}                                {  return symbol(cTILDAAT); }


  {mLP}                                     {  return symbol(cLEFT_PAREN); }
  {mRP}                                     {  return symbol(cRIGHT_PAREN); }
  {mLS}                                     {  return symbol(cLEFT_SQUARE); }
  {mRS}                                     {  return symbol(cRIGHT_SQUARE); }
  {mLC}                                     {  return symbol(cLEFT_CURLY); }
  {mRC}                                     {  return symbol(cRIGHT_CURLY); }


}

// Anything else is should be marked as a bad char
.                                           {  return symbol(cBAD_CHARACTER); }




