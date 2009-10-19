/*
 * Copyright 2000-2009 Red Shark Technology
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jetbrains.plugins.clojure.lexer;


import java.util.*;
import java.io.CharArrayReader;
import clojure.lang.IPersistentMap;
import clojure.lang.RT;
import clojure.lang.Symbol;
import clojure.lang.Keyword;
import clojure.lang.LispReader;
	
%%

%class _ClojureLexer
%implements ClojureTokenTypes, FlexLexer
%unicode
%public

%function advance
%type IPersistentMap

%eof{ return;
%eof}

////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
/////////////////////// User code //////////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

%{
//From LispReader
static final Symbol QUOTE = Symbol.create("quote");
static final Symbol THE_VAR = Symbol.create("var");
//static Symbol SYNTAX_QUOTE = Symbol.create(null, "syntax-quote");
static Symbol UNQUOTE = Symbol.create("clojure.core", "unquote");
static Symbol UNQUOTE_SPLICING = Symbol.create("clojure.core", "unquote-splicing");
static Symbol CONCAT = Symbol.create("clojure.core", "concat");
static Symbol SEQ = Symbol.create("clojure.core", "seq");
static Symbol LIST = Symbol.create("clojure.core", "list");
static Symbol APPLY = Symbol.create("clojure.core", "apply");
static Symbol HASHMAP = Symbol.create("clojure.core", "hash-map");
static Symbol HASHSET = Symbol.create("clojure.core", "hash-set");
static Symbol VECTOR = Symbol.create("clojure.core", "vector");
static Symbol WITH_META = Symbol.create("clojure.core", "with-meta");
static Symbol META = Symbol.create("clojure.core", "meta");
static Symbol DEREF = Symbol.create("clojure.core", "deref");


static IFn[] macros = new IFn[256];
static IFn[] dispatchMacros = new IFn[256];

static Pattern symbolPat = Pattern.compile("[:]?([\\D&&[^/]].*/)?([\\D&&[^/]][^/]*)");
static Pattern intPat =
		Pattern.compile(
				"([-+]?)(?:(0)|([1-9][0-9]*)|0[xX]([0-9A-Fa-f]+)|0([0-7]+)|([1-9][0-9]?)[rR]([0-9A-Za-z]+)|0[0-9]+)");
static Pattern ratioPat = Pattern.compile("([-+]?[0-9]+)/([0-9]+)");
static Pattern floatPat = Pattern.compile("([-+]?[0-9]+(\\.[0-9]*)?([eE][-+]?[0-9]+)?)(M)?");
static final Symbol SLASH = Symbol.create("/");
static final Symbol CLOJURE_SLASH = Symbol.create("clojure.core","/");


//symbol->gensymbol
static Var GENSYM_ENV = Var.create(null);
//sorted-map num->gensymbol
static Var ARG_ENV = Var.create(null);

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
final static clojure.lang.Var symNS_SEP = RT.var("org.enclojure.ide.tokens","symNS_SEP");
	final static clojure.lang.Var cWHITESPACE = RT.var("org.enclojure.ide.tokens","cWHITESPACE");
	final static clojure.lang.Var symIMPLICIT_ARG = RT.var("org.enclojure.ide.tokens","symIMPLICIT_ARG");
	final static clojure.lang.Var cWHITESPACE_SET = RT.var("org.enclojure.ide.tokens","cWHITESPACE_SET");
	final static clojure.lang.Var cRIGHT_SQUARE = RT.var("org.enclojure.ide.tokens","cRIGHT_SQUARE");
	final static clojure.lang.Var cSHARPUP = RT.var("org.enclojure.ide.tokens","cSHARPUP");
	final static clojure.lang.Var get-java-def = RT.var("org.enclojure.ide.tokens","get-java-def");
	final static clojure.lang.Var cTRUE = RT.var("org.enclojure.ide.tokens","cTRUE");
	final static clojure.lang.Var cBIG_INT_LITERAL = RT.var("org.enclojure.ide.tokens","cBIG_INT_LITERAL");
	final static clojure.lang.Var -token-meta- = RT.var("org.enclojure.ide.tokens","-token-meta-");
	final static clojure.lang.Var cREADABLE_TEXT = RT.var("org.enclojure.ide.tokens","cREADABLE_TEXT");
	final static clojure.lang.Var cTILDAAT = RT.var("org.enclojure.ide.tokens","cTILDAAT");
	final static clojure.lang.Var cEOF = RT.var("org.enclojure.ide.tokens","cEOF");
	final static clojure.lang.Var cRIGHT_PAREN = RT.var("org.enclojure.ide.tokens","cRIGHT_PAREN");
	final static clojure.lang.Var symATOM = RT.var("org.enclojure.ide.tokens","symATOM");
	final static clojure.lang.Var cLEFT_CURLY = RT.var("org.enclojure.ide.tokens","cLEFT_CURLY");
	final static clojure.lang.Var cBAD_CHARACTER = RT.var("org.enclojure.ide.tokens","cBAD_CHARACTER");
	final static clojure.lang.Var cINTEGER_LITERAL = RT.var("org.enclojure.ide.tokens","cINTEGER_LITERAL");
	final static clojure.lang.Var cNIL = RT.var("org.enclojure.ide.tokens","cNIL");
	final static clojure.lang.Var cLONG_LITERAL = RT.var("org.enclojure.ide.tokens","cLONG_LITERAL");
	final static clojure.lang.Var cBOOLEAN_LITERAL = RT.var("org.enclojure.ide.tokens","cBOOLEAN_LITERAL");
	final static clojure.lang.Var cCOLON_SYMBOL = RT.var("org.enclojure.ide.tokens","cCOLON_SYMBOL");
	final static clojure.lang.Var cSHARP = RT.var("org.enclojure.ide.tokens","cSHARP");
	final static clojure.lang.Var cDOUBLE_LITERAL = RT.var("org.enclojure.ide.tokens","cDOUBLE_LITERAL");
	final static clojure.lang.Var cLEFT_SQUARE = RT.var("org.enclojure.ide.tokens","cLEFT_SQUARE");
	final static clojure.lang.Var cBIG_DECIMAL_LITERAL = RT.var("org.enclojure.ide.tokens","cBIG_DECIMAL_LITERAL");
	final static clojure.lang.Var cWRONG_STRING_LITERAL = RT.var("org.enclojure.ide.tokens","cWRONG_STRING_LITERAL");
	final static clojure.lang.Var cFLOAT_LITERAL = RT.var("org.enclojure.ide.tokens","cFLOAT_LITERAL");
	final static clojure.lang.Var cCHAR_LITERAL = RT.var("org.enclojure.ide.tokens","cCHAR_LITERAL");
	final static clojure.lang.Var cTILDA = RT.var("org.enclojure.ide.tokens","cTILDA");
	final static clojure.lang.Var cRIGHT_CURLY = RT.var("org.enclojure.ide.tokens","cRIGHT_CURLY");
	final static clojure.lang.Var make-token = RT.var("org.enclojure.ide.tokens","make-token");
	final static clojure.lang.Var cQUOTE = RT.var("org.enclojure.ide.tokens","cQUOTE");
	final static clojure.lang.Var cUP = RT.var("org.enclojure.ide.tokens","cUP");
	final static clojure.lang.Var cCOMMA = RT.var("org.enclojure.ide.tokens","cCOMMA");
	final static clojure.lang.Var symS = RT.var("org.enclojure.ide.tokens","symS");
	final static clojure.lang.Var cAT = RT.var("org.enclojure.ide.tokens","cAT");
	final static clojure.lang.Var cCOMMENTS = RT.var("org.enclojure.ide.tokens","cCOMMENTS");
	final static clojure.lang.Var cLINE_COMMENT = RT.var("org.enclojure.ide.tokens","cLINE_COMMENT");
	final static clojure.lang.Var cFALSE = RT.var("org.enclojure.ide.tokens","cFALSE");
	final static clojure.lang.Var cRATIO = RT.var("org.enclojure.ide.tokens","cRATIO");
	final static clojure.lang.Var cLEFT_PAREN = RT.var("org.enclojure.ide.tokens","cLEFT_PAREN");
	final static clojure.lang.Var symDOT = RT.var("org.enclojure.ide.tokens","symDOT");
	final static clojure.lang.Var cSEPARATORS = RT.var("org.enclojure.ide.tokens","cSEPARATORS");
	final static clojure.lang.Var cIDENTIFIERS = RT.var("org.enclojure.ide.tokens","cIDENTIFIERS");
	final static clojure.lang.Var cLITERALS = RT.var("org.enclojure.ide.tokens","cLITERALS");
	final static clojure.lang.Var cSTRING_LITERAL = RT.var("org.enclojure.ide.tokens","cSTRING_LITERAL");
	final static clojure.lang.Var cEOL = RT.var("org.enclojure.ide.tokens","cEOL");
	final static clojure.lang.Var cBACKQUOTE = RT.var("org.enclojure.ide.tokens","cBACKQUOTE");
	final static clojure.lang.Var cSTRINGS = RT.var("org.enclojure.ide.tokens","cSTRINGS");
	final static clojure.lang.Var cATOMS = RT.var("org.enclojure.ide.tokens","cATOMS");

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
  "."                                       {  return symDOT; }
  "/"                                       {  return symNS_SEP; }
  ({mNoDigit1} | {mDIGIT} | ":")+           {  return symATOM; }
  (({mNoDigit1} | {mDIGIT} | ":")+)? "#"    {  yybegin(YYINITIAL); return symATOM; }
  [^]                                       {  yypushback(yytext().length()); yybegin(YYINITIAL); }
}

<YYINITIAL>{

  {mLINE_COMMENT}                           {  return cLINE_COMMENT; }
  
  {mWS}+                                    {  return cWHITESPACE; }
  {mCOMMA}                                  {  return cCOMMA; }

  {mSTRING}                                 {  return cSTRING_LITERAL; }
  {mWRONG_STRING }                          {  return cWRONG_STRING_LITERAL; }

  {mCHAR}                                   {  return cCHAR_LITERAL; }
  {mNIL}                                    {  return cNIL; }
  {mTRUE}                                   {  return cTRUE; }
  {mFALSE}                                  {  return cFALSE; }

  {mNUM_INT}                                {  return cINTEGER_LITERAL; }
  {mNUM_LONG}                               {  return cLONG_LITERAL; }
  {mNUM_BIG_INT}                            {  return cBIG_INT_LITERAL; }
  {mNUM_FLOAT}                              {  return cFLOAT_LITERAL; }
  {mNUM_DOUBLE}                             {  return cDOUBLE_LITERAL; }
  {mNUM_BIG_DECIMAL}                        {  return cBIG_DECIMAL_LITERAL; }
  {mRATIO}                                  {  return cRATIO; }

  // Reserved symbols
  "/"                                       {  return symATOM; }
  "."{mIDENT} | {mIDENT}"."                 {  return symATOM; }
  {mIDENT}                                  {  yypushback(yytext().length()); yybegin(cSYMBOL); }
  {mKEY}                                    {  return cCOLON_SYMBOL; }


  {mQUOTE}                                  {  return cQUOTE; }
  {mBACKQUOTE}                              {  return cBACKQUOTE; }
  {mSHARPUP}                                {  return cSHARPUP; }
  {mSHARP}                                  {  return cSHARP; }
  {mUP}                                     {  return cUP; }
  {mIMPLICIT_ARG}                           {  return symIMPLICIT_ARG; }
  {mTILDA}                                  {  return cTILDA; }
  {mAT}                                     {  return cAT; }
  {mTILDAAT}                                {  return cTILDAAT; }


  {mLP}                                     {  return cLEFT_PAREN; }
  {mRP}                                     {  return cRIGHT_PAREN; }
  {mLS}                                     {  return cLEFT_SQUARE; }
  {mRS}                                     {  return cRIGHT_SQUARE; }
  {mLC}                                     {  return cLEFT_CURLY; }
  {mRC}                                     {  return cRIGHT_CURLY; }


}

// Anything else is should be marked as a bad char
.                                           {  return cBAD_CHARACTER; }




