
package micromod;

public class Instrument {
	public static final int FP_SHIFT = 15, FP_ONE = 1 << FP_SHIFT, FP_MASK = FP_ONE - 1;
	
	private String name = "";
	private int volume, fineTune, loopStart, loopLength;
	private byte[] sampleData = new byte[ 0 ];
	
	public String getName() {
		return name;
	}
	
	public void setName( String name ) {
		if( name == null ) {
			this.name = "";
		} else if( name.length() > 22 ) {
			this.name = name.substring( 0, 22 );
		} else {
			this.name = name;
		}
	}

	public int getVolume() {
		return volume;
	}
	
	public void setVolume( int volume ) {
		if( volume < 0 || volume > 64 ) {
			throw new IllegalArgumentException( "Instrument volume out of range (0 to 64): " + volume );
		}
		this.volume = volume;
	}
	
	public int getFineTune() {
		return fineTune;
	}
	
	public void setFineTune( int fineTune ) {
		if( fineTune < -8 || fineTune > 7 ) {
			throw new IllegalArgumentException( "Instrument fine tune out of range (-8 to 7): " + fineTune );
		}
		this.fineTune = fineTune;
	}
	
	public void setSampleData( byte[] sampleData, int loopStart, int loopLength ) {
		setSampleData( sampleData, 0, sampleData.length, loopStart, loopLength );
	}
	
	public void setSampleData( byte[] sampleData, int sampleOffset, int sampleLength, int loopStart, int loopLength ) {
		if( sampleOffset + sampleLength > sampleData.length ) {
			sampleLength = sampleData.length - sampleOffset;
		}
		if( loopStart + loopLength > sampleLength ) {
			loopLength = sampleLength - loopStart;
		}
		if( loopStart < 0 || loopStart >= sampleLength || loopLength < 4 ) {
			loopStart = sampleLength;
			loopLength = 0;
		}
		int loopEnd = ( loopStart & -2 ) + ( loopLength & -2 );
		if( loopEnd > 0x1FFFE ) {
			throw new IllegalArgumentException( "Sample data length out of range (0-131070): " + loopEnd );
		}
		this.loopStart = loopStart & -2;
		this.loopLength = loopLength & -2;
		this.sampleData = new byte[ loopEnd + 1 ];
		System.arraycopy( sampleData, sampleOffset, this.sampleData, 0, loopEnd );
		/* The sample after the loop end must be the same as the loop start for the interpolation algorithm. */
		this.sampleData[ loopEnd ] = this.sampleData[ this.loopStart ];
	}
	
	public int getLoopStart() {
		return loopStart;
	}
	
	public int getLoopLength() {
		return loopLength;
	}
	
	public void getAudio( int sampleIdx, int sampleFrac, int step, int leftGain, int rightGain,
		int[] mixBuffer, int offset, int count, boolean interpolation ) {
		int loopEnd = loopStart + loopLength;
		int mixIdx = offset << 1;
		int mixEnd = ( offset + count ) << 1;
		if( interpolation ) {
			while( mixIdx < mixEnd ) {
				if( sampleIdx >= loopEnd ) {
					if( loopLength < 2 ) {
						break;
					}
					while( sampleIdx >= loopEnd ) {
						sampleIdx -= loopLength;
					}
				}
				int c = sampleData[ sampleIdx ];
				int m = sampleData[ sampleIdx + 1 ] - c;
				int y = ( ( m * sampleFrac ) >> ( FP_SHIFT - 8 ) ) + ( c << 8 );
				mixBuffer[ mixIdx++ ] += ( y * leftGain ) >> FP_SHIFT;
				mixBuffer[ mixIdx++ ] += ( y * rightGain ) >> FP_SHIFT;
				sampleFrac += step;
				sampleIdx += sampleFrac >> FP_SHIFT;
				sampleFrac &= FP_MASK;
			}
		} else {
			while( mixIdx < mixEnd ) {
				if( sampleIdx >= loopEnd ) {
					if( loopLength < 2 ) {
						break;
					}
					while( sampleIdx >= loopEnd ) {
						sampleIdx -= loopLength;
					}
				}
				int y = sampleData[ sampleIdx ] << 8;
				mixBuffer[ mixIdx++ ] += ( y * leftGain ) >> FP_SHIFT;
				mixBuffer[ mixIdx++ ] += ( y * rightGain ) >> FP_SHIFT;
				sampleFrac += step;
				sampleIdx += sampleFrac >> FP_SHIFT;
				sampleFrac &= FP_MASK;
			}
		}
	}

	public int normalizeSampleIdx( int sampleIdx ) {
		int loopOffset = sampleIdx - loopStart;
		if( loopOffset > 0 ) {
			sampleIdx = loopStart;
			if( loopLength > 1 ) {
				sampleIdx += loopOffset % loopLength;
			}
		}
		return sampleIdx;
	}

	public int load( byte[] module, int instIdx, int sampleDataOffset ) {
		char[] name = new char[ 22 ];
		for( int idx = 0; idx < name.length; idx++ ) {
			int chr = module[ instIdx * 30 - 10 + idx ] & 0xFF;
			name[ idx ] = chr > 32 ? ( char ) chr : 32;
		}
		setName( new String( name ) );
		int sampleLength = ushortbe( module, instIdx * 30 + 12 ) * 2;
		int fineTune = module[ instIdx * 30 + 14 ] & 0xF;
		setFineTune( fineTune > 7 ? fineTune - 16 : fineTune );
		int volume =  module[ instIdx * 30 + 15 ] & 0x7F;
		setVolume( volume > 64 ? 64 : volume );
		int loopStart = ushortbe( module, instIdx * 30 + 16 ) * 2;
		int loopLength = ushortbe( module, instIdx * 30 + 18 ) * 2;
		setSampleData( module, sampleDataOffset, sampleLength, loopStart, loopLength );
		return sampleDataOffset + sampleLength;
	}
	
	public int save( byte[] module, int instIdx, int sampleDataOffset ) {
		int sampleLength = loopStart + loopLength;
		if( module != null ) {
			for( int idx = 0; idx < 22; idx++ ) {
				int chr = idx < name.length() ? name.charAt( idx ) : 32;
				module[ instIdx * 30 - 10 + idx ] = ( byte ) chr;
			}
			module[ instIdx * 30 + 12 ] = ( byte ) ( sampleLength >> 9 );
			module[ instIdx * 30 + 13 ] = ( byte ) ( sampleLength >> 1 );
			module[ instIdx * 30 + 14 ] = ( byte ) ( fineTune < 0 ? fineTune + 16 : fineTune );
			module[ instIdx * 30 + 15 ] = ( byte ) volume;
			module[ instIdx * 30 + 16 ] = ( byte ) ( loopStart >> 9 );
			module[ instIdx * 30 + 17 ] = ( byte ) ( loopStart >> 1 );
			module[ instIdx * 30 + 18 ] = ( byte ) ( loopLength >> 9 );
			module[ instIdx * 30 + 19 ] = ( byte ) ( loopLength >> 1 );
			System.arraycopy( sampleData, 0, module, sampleDataOffset, sampleLength );
		}
		return sampleDataOffset + sampleLength;
	}
	
	private static int ushortbe( byte[] buf, int offset ) {
		return ( ( buf[ offset ] & 0xFF ) << 8 ) | ( buf[ offset + 1 ] & 0xFF );
	}
}
