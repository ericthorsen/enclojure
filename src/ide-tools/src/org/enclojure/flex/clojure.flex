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
package org.enclojure.flex;

import java.util.*;
import java.io.CharArrayReader;
import clojure.lang.Var;
import clojure.lang.RT;
import clojure.lang.IFn;
import clojure.lang.Var;
import clojure.lang.Keyword;
import clojure.lang.LispReader;
import clojure.lang.Symbol;
import clojure.lang.IPersistentMap;
import java.util.regex.Pattern;
import java.util.logging.*;
import java_cup.runtime.*;
import org.enclojure.flex.ClojureSym;

%%

%class _Lexer
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

%eofval{
    return symbol(cEOF);
%eofval}
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
/////////////////////// User code //////////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

%{

    final Var requireFn = RT.var("clojure.core","require");

    public ClojureSym symbol(Var tokenType,Object data)
    {
        return ClojureSym.create((IPersistentMap)tokenType.get(),yyline, yycolumn,yychar,data);
    }

    public ClojureSym symbol(Var tokenType)
    {
        return ClojureSym.create((IPersistentMap)tokenType.get(),yyline, yycolumn,yychar);
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
	public final static Var cSTRING_LITERAL = (Var)RT.var("org.enclojure.idetools.tokens","cSTRING-LITERAL");
	public final static Var cWRONG_STRING_LITERAL = (Var)RT.var("org.enclojure.idetools.tokens","cWRONG-STRING-LITERAL");
	public final static Var cLONG_LITERAL = (Var)RT.var("org.enclojure.idetools.tokens","cLONG-LITERAL");
	public final static Var cWHITESPACE = (Var)RT.var("org.enclojure.idetools.tokens","cWHITESPACE");
	public final static Var cLINE_COMMENT = (Var)RT.var("org.enclojure.idetools.tokens","cLINE-COMMENT");
	public final static Var cCOLON_SYMBOL = (Var)RT.var("org.enclojure.idetools.tokens","cCOLON-SYMBOL");
	public final static Var cSHARPUP = (Var)RT.var("org.enclojure.idetools.tokens","cSHARPUP");
	public final static Var cRIGHT_SQUARE = (Var)RT.var("org.enclojure.idetools.tokens","cRIGHT-SQUARE");
	public final static Var get_java_def = (Var)RT.var("org.enclojure.idetools.tokens","get-java-def");
	public final static Var cTRUE = (Var)RT.var("org.enclojure.idetools.tokens","cTRUE");
	public final static Var _token_meta_ = (Var)RT.var("org.enclojure.idetools.tokens","-token-meta-");
	public final static Var cTILDAAT = (Var)RT.var("org.enclojure.idetools.tokens","cTILDAAT");
	public final static Var cEOF = (Var)RT.var("org.enclojure.idetools.tokens","cEOF");
	public final static Var symATOM = (Var)RT.var("org.enclojure.idetools.tokens","symATOM");
	public final static Var cRIGHT_PAREN = (Var)RT.var("org.enclojure.idetools.tokens","cRIGHT-PAREN");
	public final static Var cBIG_DECIMAL_LITERAL = (Var)RT.var("org.enclojure.idetools.tokens","cBIG-DECIMAL-LITERAL");
	public final static Var cNIL = (Var)RT.var("org.enclojure.idetools.tokens","cNIL");
	public final static Var cWHITESPACE_SET = (Var)RT.var("org.enclojure.idetools.tokens","cWHITESPACE-SET");
	public final static Var symIMPLICIT_ARG = (Var)RT.var("org.enclojure.idetools.tokens","symIMPLICIT-ARG");
	public final static Var cFLOAT_LITERAL = (Var)RT.var("org.enclojure.idetools.tokens","cFLOAT-LITERAL");
	public final static Var cCHAR_LITERAL = (Var)RT.var("org.enclojure.idetools.tokens","cCHAR-LITERAL");
	public final static Var cLEFT_PAREN = (Var)RT.var("org.enclojure.idetools.tokens","cLEFT-PAREN");
	public final static Var cSHARP = (Var)RT.var("org.enclojure.idetools.tokens","cSHARP");
	public final static Var cDOUBLE_LITERAL = (Var)RT.var("org.enclojure.idetools.tokens","cDOUBLE-LITERAL");
	public final static Var cBIG_INT_LITERAL = (Var)RT.var("org.enclojure.idetools.tokens","cBIG-INT-LITERAL");
	public final static Var cLEFT_SQUARE = (Var)RT.var("org.enclojure.idetools.tokens","cLEFT-SQUARE");
	public final static Var cLEFT_CURLY = (Var)RT.var("org.enclojure.idetools.tokens","cLEFT-CURLY");
	public final static Var cTILDA = (Var)RT.var("org.enclojure.idetools.tokens","cTILDA");
	public final static Var cREADABLE_TEXT = (Var)RT.var("org.enclojure.idetools.tokens","cREADABLE-TEXT");
	public final static Var symNS_SEP = (Var)RT.var("org.enclojure.idetools.tokens","symNS-SEP");
	public final static Var make_token = (Var)RT.var("org.enclojure.idetools.tokens","make-token");
	public final static Var cQUOTE = (Var)RT.var("org.enclojure.idetools.tokens","cQUOTE");
	public final static Var cUP = (Var)RT.var("org.enclojure.idetools.tokens","cUP");
	public final static Var cRIGHT_CURLY = (Var)RT.var("org.enclojure.idetools.tokens","cRIGHT-CURLY");
	public final static Var cCOMMA = (Var)RT.var("org.enclojure.idetools.tokens","cCOMMA");
	public final static Var symS = (Var)RT.var("org.enclojure.idetools.tokens","symS");
	public final static Var cAT = (Var)RT.var("org.enclojure.idetools.tokens","cAT");
	public final static Var cCOMMENTS = (Var)RT.var("org.enclojure.idetools.tokens","cCOMMENTS");
	public final static Var cFALSE = (Var)RT.var("org.enclojure.idetools.tokens","cFALSE");
	public final static Var make_token_set = (Var)RT.var("org.enclojure.idetools.tokens","make-token-set");
	public final static Var cRATIO = (Var)RT.var("org.enclojure.idetools.tokens","cRATIO");
	public final static Var symDOT = (Var)RT.var("org.enclojure.idetools.tokens","symDOT");
	public final static Var _TOKEN_TYPES_MAP_ = (Var)RT.var("org.enclojure.idetools.tokens","-TOKEN-TYPES-MAP-");
	public final static Var _TOKEN_TYPES_BY_ID_ = (Var)RT.var("org.enclojure.idetools.tokens","-TOKEN-TYPES-BY-ID-");
	public final static Var cSEPARATORS = (Var)RT.var("org.enclojure.idetools.tokens","cSEPARATORS");
	public final static Var cINTEGER_LITERAL = (Var)RT.var("org.enclojure.idetools.tokens","cINTEGER-LITERAL");
	public final static Var cIDENTIFIERS = (Var)RT.var("org.enclojure.idetools.tokens","cIDENTIFIERS");
	public final static Var cLITERALS = (Var)RT.var("org.enclojure.idetools.tokens","cLITERALS");
	public final static Var cBOOLEAN_LITERAL = (Var)RT.var("org.enclojure.idetools.tokens","cBOOLEAN-LITERAL");
	public final static Var cBAD_CHARACTER = (Var)RT.var("org.enclojure.idetools.tokens","cBAD-CHARACTER");
	public final static Var cEOL = (Var)RT.var("org.enclojure.idetools.tokens","cEOL");
	public final static Var cBACKQUOTE = (Var)RT.var("org.enclojure.idetools.tokens","cBACKQUOTE");
	public final static Var cSTRINGS = (Var)RT.var("org.enclojure.idetools.tokens","cSTRINGS");
	public final static Var cATOMS = (Var)RT.var("org.enclojure.idetools.tokens","cATOMS");

%}
%init{
    try {
        requireFn.invoke(Symbol.create("org.enclojure.idetools.tokens"));
        requireFn.invoke(Symbol.create("org.enclojure.idetools.token-set"));
    } catch (Exception ex) {
            Logger.getLogger("org.enclojure.flex._Lexer").log(Level.SEVERE, null, ex);
        }
%init}

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

  {mSTRING}                                 {  return symbol(cSTRING_LITERAL,yytext()); }
  {mWRONG_STRING }                          {  return symbol(cWRONG_STRING_LITERAL); }

  {mCHAR}                                   {  return symbol(cCHAR_LITERAL,yytext()); }
  {mNIL}                                    {  return symbol(cNIL); }
  {mTRUE}                                   {  return symbol(cTRUE,yytext()); }
  {mFALSE}                                  {  return symbol(cFALSE,yytext()); }

  {mNUM_INT}                                {  return symbol(cINTEGER_LITERAL,yytext()); }
  {mNUM_LONG}                               {  return symbol(cLONG_LITERAL,yytext()); }
  {mNUM_BIG_INT}                            {  return symbol(cBIG_INT_LITERAL,yytext()); }
  {mNUM_FLOAT}                              {  return symbol(cFLOAT_LITERAL,yytext()); }
  {mNUM_DOUBLE}                             {  return symbol(cDOUBLE_LITERAL,yytext()); }
  {mNUM_BIG_DECIMAL}                        {  return symbol(cBIG_DECIMAL_LITERAL,yytext()); }
  {mRATIO}                                  {  return symbol(cRATIO,yytext()); }

  // Reserved symbols
  "/"                                       {  return symbol(symATOM,yytext()); }
  "."{mIDENT} | {mIDENT}"."                 {  return symbol(symATOM,yytext()); }
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
