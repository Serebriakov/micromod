
package projacker;

public class Parser {
	public static void parse( java.io.Reader input, Element element ) throws java.io.IOException {
		Element context = null;
		char[] buf = new char[ 512 ];
		int len, chr = input.read();
		while( chr > 0 && chr <= 32 ) {
			/* Skip whitespace.*/
			chr = input.read();
		}
		while( chr > 0 ) {
			while( chr == '(' ) {
				/* Skip comments.*/
				while( chr > 0 && chr != ')' ) {
					chr = input.read();
				}
				if( chr == ')' ) {
					chr = input.read();
				}
				while( chr > 0 && chr <= 32 ) {
					chr = input.read();
				}
			}
			len = 0;
			while( chr > 32 ) {
				/* Read token.*/
				buf[ len++ ] = ( char ) chr;
				chr = input.read();
			}
			while( chr > 0 && chr <= 32 ) {
				/* Skip whitespace.*/
				chr = input.read();
			}
			String token = new String( buf, 0, len );
			len = 0;
			if( chr == '"' ) {
				/* Quote-delimited value. */
				chr = input.read();
				while( chr > 0 && chr != '"' ) {
					buf[ len++ ] = ( char ) chr;
					chr = input.read();
				}
				if( chr == '"' ) {
					chr = input.read();
				}
			} else {
				/* Whitespace-delimited value.*/
				while( chr > 32 ) {
					buf[ len++ ] = ( char ) chr;
					chr = input.read();
				}
			}
			while( chr > 0 && chr <= 32 ) {
				/* Skip whitespace.*/
				chr = input.read();
			}
			String param = new String( buf, 0, len );
			if( context == null ) {
				context = element;
			} else if( context.getChild() == null ) {
				context.end();
			} else {
				context = context.getChild();
			}
			while( context != null && !token.equals( context.getToken() ) ) {
				if( context.getSibling() != null ) {
					context = context.getSibling();
				} else {
					context = context.getParent();
					if( context != null ) {
						context.end();
					}
				}
			}
			if( context != null ) {
				context.begin( param );
			} else if( token.length() > 0 ) {
				throw new IllegalArgumentException( "Invalid token: " + token );
			}
		}
		while( context != null ) {
			context.end();
			context = context.getParent();
		}
	}
	
	/* Split a string, separated by whitespace or separator. */
	public static String[] split( String input, char separator ) {
		String[] output = new String[ split( input, ',', null ) ];
		split( input, ',', output );
		return output;
	}
	
	public static int split( String input, char separator, String[] output ) {
		int outIdx = 0, inLen = input.length(), start = 0;
		for( int inIdx = 0; inIdx <= inLen; inIdx++ ) {
			char chr = inIdx < inLen ? input.charAt( inIdx ) : separator;
			if( chr < 33 || chr == separator ) {
				if( inIdx > start ) {
					if( output != null && outIdx < output.length ) {
						output[ outIdx ] = input.substring( start, inIdx );
					}
					outIdx++;
				}
				start = inIdx + 1;
			}
		}
		return outIdx;
	}
	
	public static int[] parseIntegerArray( String param ) {
		String[] input = split( param, ',' );
		int[] output = new int[ input.length ];
		for( int idx = 0; idx < input.length; idx++ ) {
			output[ idx ] = parseInteger( input[ idx ] );
		}
		return output;
	}
	
	public static int parseInteger( String param ) {
		int idx = 0, len = param.length(), a = 0, s = 1;
		if( idx < len ) {
			char chr = param.charAt( idx++ );
			if( idx < len && chr == '-' ) {
				s = -1;
				chr = param.charAt( idx++ );
			}
			while( chr >= '0' && chr <= '9' ) {
				a = a * 10 + chr - '0';
				chr = idx < len ? param.charAt( idx++ ) : ',';
			}
			if( idx < len ) {
				throw new IllegalArgumentException( "Invalid character: " + chr );
			}
		}
		return a * s;
	}
}
