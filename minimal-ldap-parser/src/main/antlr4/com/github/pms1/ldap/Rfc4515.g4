grammar Rfc4515;

@parser::members {
   boolean lenient = false;
}
   
filterEOF: filter EOF;

// https://tools.ietf.org/search/rfc4512, Section 1.4
//      keystring = leadkeychar *keychar
keystring: leadkeychar keychar*;
//      leadkeychar = ALPHA
leadkeychar: ALPHA;
//      keychar = ALPHA / DIGIT / HYPHEN
keychar: ALPHA|DIGIT|HYPHEN
| {lenient}? DOT;

//      number  = DIGIT / ( LDIGIT 1*DIGIT )
//
//      ALPHA   = %x41-5A / %x61-7A   ; "A"-"Z" / "a"-"z"
ALPHA : [A-Za-z];
//      DIGIT   = %x30 / LDIGIT       ; "0"-"9"
DIGIT: '0' | LDIGIT;
//      LDIGIT  = %x31-39             ; "1"-"9"
LDIGIT: [1-9];
//      HEX     = DIGIT / %x41-46 / %x61-66 ; "0"-"9" / "A"-"F" / "a"-"f"
//
//      SP      = 1*SPACE  ; one or more " "
//      WSP     = 0*SPACE  ; zero or more " "
//      NULL    = %x00 ; null (0)
//      SPACE   = %x20 ; space (" ")
SPACE : ' ';
//      DQUOTE  = %x22 ; quote (""")
//      SHARP   = %x23 ; octothorpe (or sharp sign) ("#")
//      DOLLAR  = %x24 ; dollar sign ("$")
//      SQUOTE  = %x27 ; single quote ("'")
//      LPAREN  = %x28 ; left paren ("(")
LPAREN : '(';
//      RPAREN  = %x29 ; right paren (")")
RPAREN : ')';
//      PLUS    = %x2B ; plus sign ("+")
//      COMMA   = %x2C ; comma (",")
//      HYPHEN  = %x2D ; hyphen ("-")
HYPHEN : '-';
//      DOT     = %x2E ; period (".")
DOT : '.';
//      SEMI    = %x3B ; semicolon (";")
SEMI : ';';
//      LANGLE  = %x3C ; left angle bracket ("<")
//      EQUALS  = %x3D ; equals sign ("=")
EQUALS : '=';
//      RANGLE  = %x3E ; right angle bracket (">")
//      ESC     = %x5C ; backslash ("\")
//      USCORE  = %x5F ; underscore ("_")
//      LCURLY  = %x7B ; left curly brace "{"
//      RCURLY  = %x7D ; right curly brace "}"
//
//      ; Any UTF-8 [RFC3629] encoded Unicode [Unicode] character
//      UTF8    = UTF1 / UTFMB
//      UTFMB   = UTF2 / UTF3 / UTF4
//      UTF0    = %x80-BF
//      UTF1    = %x00-7F
//      UTF2    = %xC2-DF UTF0
//      UTF3    = %xE0 %xA0-BF UTF0 / %xE1-EC 2(UTF0) /
//                %xED %x80-9F UTF0 / %xEE-EF 2(UTF0)
//      UTF4    = %xF0 %x90-BF 2(UTF0) / %xF1-F3 3(UTF0) /
//                %xF4 %x80-8F 2(UTF0)
//
//      OCTET   = %x00-FF ; Any octet (8-bit data unit)
//      numericoid = number 1*( DOT number )
//      descr = keystring
descr: keystring;

//      oid = descr / numericoid
oid: descr;

// https://tools.ietf.org/search/rfc4512, Section 2.5
//      attributedescription = attributetype options
attributedescription: attributetype options;

//      attributetype = oid
attributetype: oid;

//      options = *( SEMI option )
options: (SEMI children+=option)*;

//      option = 1*keychar
option: keychar+;
            
// https://tools.ietf.org/search/rfc4515

//   filter         = LPAREN filtercomp RPAREN
filter: LPAREN filtercomp RPAREN;

//      filtercomp     = and / or / not / item
filtercomp: 
  and
| or
| not
| item
; 

//      and            = AMPERSAND filterlist
and: AMPERSAND filterlist
| {lenient}?  AMPERSAND SPACE+ filterlist;

//      or             = VERTBAR filterlist
or: VERTBAR filterlist;

//      not            = EXCLAMATION filter
not: EXCLAMATION filter;
//      filterlist     = 1*filter
filterlist: filter+;

//      item           = simple / present / substring / extensible
item: simple;

//      simple         = attr filtertype assertionvalue
simple: attr filtertype assertionvalue;
//      filtertype     = equal / approx / greaterorequal / lessorequal
filtertype : equal;
//      equal          = EQUALS
equal: EQUALS;
//      approx         = TILDE EQUALS
//      greaterorequal = RANGLE EQUALS
//      lessorequal    = LANGLE EQUALS
//      extensible     = ( attr [dnattrs]
//                           [matchingrule] COLON EQUALS assertionvalue )
//                       / ( [dnattrs]
//                            matchingrule COLON EQUALS assertionvalue )
//      present        = attr EQUALS ASTERISK
//      substring      = attr EQUALS [initial] any [final]
//      initial        = assertionvalue
//      any            = ASTERISK *(assertionvalue ASTERISK)
//      final          = assertionvalue
//      attr           = attributedescription
//                         ; The attributedescription rule is defined in
//                         ; Section 2.5 of [RFC4512].
attr: attributedescription;
//      dnattrs        = COLON "dn"
//      matchingrule   = COLON oid
//      assertionvalue = valueencoding
assertionvalue: valueencoding;
//      ; The <valueencoding> rule is used to encode an <AssertionValue>
//      ; from Section 4.1.6 of [RFC4511].
//      valueencoding  = 0*(normal / escaped)
valueencoding: (normal)*;

//      normal         = UTF1SUBSET / UTFMB
normal: utf1subset | UTFMB;
//      escaped        = ESC HEX HEX
//      EXCLAMATION    = %x21 ; exclamation mark ("!")
EXCLAMATION : '!';
//      AMPERSAND      = %x26 ; ampersand (or AND symbol) ("&")
AMPERSAND : '&';

//      ASTERISK       = %x2A ; asterisk ("*")
//      COLON          = %x3A ; colon (":")
//      VERTBAR        = %x7C ; vertical bar (or pipe) ("|")
VERTBAR : '|';
//      TILDE          = %x7E ; tilde ("~")

//      UTF1SUBSET     = %x01-27 / %x2B-5B / %x5D-7F
//                          ; UTF1SUBSET excludes 0x00 (NUL), LPAREN,
//                          ; RPAREN, ASTERISK, and ESC.
utf1subset: DIGIT | ALPHA | EXCLAMATION | AMPERSAND | DOT | UTF1SUBSET | VERTBAR;
UTF1SUBSET: [\u0001-\u0027\u002B-\u005B\u005D-\u007F];

UTFMB: [\u0080-\uFFFE];
