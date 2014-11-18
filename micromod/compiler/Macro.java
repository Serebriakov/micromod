
package micromod.compiler;

public class Macro implements Element {
	public static final int BEGIN = 1, END = 2;

	private Module parent;
	private Pattern sibling;
	private Scale child = new Scale( this );
	private micromod.Pattern pattern;
	private int macroIdx, rowIdx;
	private int repeatRow, attackRows, decayRows, speed;
	private String scale, root;

	public Macro( Module parent ) {
		this.parent = parent;
		sibling = new Pattern( parent );
	}
	
	public String getToken() {
		return "Macro";
	}
	
	public Element getParent() {
		return parent;
	}
	
	public Element getSibling() {
		return sibling;
	}
	
	public Element getChild() {
		return child;
	}
	
	public void begin( String value ) {
		pattern = new micromod.Pattern( 1 );
		macroIdx = Parser.parseInteger( value );
		rowIdx = repeatRow = attackRows = decayRows = 0;
		speed = 6;
	}

	public void end() {
		micromod.Module module = parent.getModule();
		int volume = getNoteVolume( module, pattern, 0 );
		if( attackRows > 0 ) {
			volumeFade( pattern, 0, attackRows - 1, 0, volume );
		}
		if( attackRows >= 0 && decayRows > 0 ) {
			volumeFade( pattern, attackRows, attackRows + decayRows - 1, volume, 0 );
		}
		calculateSlide( module, pattern, speed );
		parent.setMacro( macroIdx, new micromod.Macro( scale, root, pattern ) );
	}

	public void setScale( String scale ) {
		this.scale = scale;
	}
	
	public void setRoot( String root ) {
		this.root = root;
	}

	public void nextNote( micromod.Note note, int repeatParam, int timeStretchRows ) {
		pattern.setNote( rowIdx++, 0, note );
		micromod.Module module = parent.getModule();
		if( timeStretchRows > 1 ) {
			micromod.Instrument instrument = module.getInstrument( note.instrument );
			int sampleLength = instrument.getLoopStart() + instrument.getLoopLength();
			note.effect = 0x9;
			for( int row = 1; row < timeStretchRows; row++ ) {
				int offset = ( sampleLength * row ) / ( timeStretchRows << 7 );
				note.parameter = ( offset >> 1 ) + ( offset & 1 );
				pattern.setNote( rowIdx++, 0, note );
			}
		}
		if( repeatParam == BEGIN ) {
			repeatRow = rowIdx;
		} else if( repeatParam > 1 ) {
			int repeatEnd = rowIdx;
			for( int idx = 1; idx < repeatParam; idx++ ) {
				for( int row = repeatRow; row < repeatEnd; row++ ) {
					pattern.getNote( row, 0, note );
					pattern.setNote( rowIdx++, 0, note );
				}
			}
		}
	}

	public void setSpeed( int ticksPerRow ) {
		speed = ticksPerRow;
	}

	public void setAttack( int rows ) {
		attackRows = rows;
	}

	public void setDecay( int rows ) {
		decayRows = rows;
	}

	private static void volumeFade( micromod.Pattern pattern, int startRow, int endRow, int startVol, int endVol ) {
		int v0 = sqrt( startVol );
		int v1 = sqrt( endVol );
		int rows = endRow - startRow + 1;
		int vm = ( v1 - v0 ) * 2 / rows;
		for( int row = 0; row < rows; row++ ) {
			int volume = vm * ( row + 1 ) + v0 * 2;
			volume = ( volume >> 1 ) + ( volume & 1 );
			setNoteVolume( pattern, startRow + row, ( volume * volume ) >> 10 );
		}
	}

	private static void calculateSlide( micromod.Module module, micromod.Pattern pattern, int speed ) {
		int volume = 0, period = 0, fineTune = 0, portaPeriod = 0, portaSpeed = 0, trans, delta;
		micromod.Note note = new micromod.Note();
		for( int row = 0; row < 64; row++ ) {
			if( note.effect == 0x1 ) {
				period = clampPeriod( period - note.parameter * ( speed - 1 ) );
			} else if( note.effect == 0x2 ) {
				period = clampPeriod( period + note.parameter * ( speed - 1 ) );
			} else if( note.effect == 0x3 || note.effect == 0x5 ) {
				if( period < portaPeriod ) {
					period = clampPeriod( period + portaSpeed * ( speed - 1 ) );
					if( period > portaPeriod ) {
						period = portaPeriod;
					}
				} else {
					period = clampPeriod( period - portaSpeed * ( speed - 1 ) );
					if( period < portaPeriod ) {
						period = portaPeriod;
					}
				}
			}
			if( note.effect == 0x5 || note.effect == 0x6 || note.effect == 0xA ) {
				volume = clampVolume( volume + ( ( ( note.parameter >> 4 ) & 0xF ) - ( note.parameter & 0xF ) ) * ( speed - 1 ) );
			}
			pattern.getNote( row, 0, note );
			if( note.instrument > 0 ) {
				if( note.effect != 0xC ) {
					volume = module.getInstrument( note.instrument ).getVolume();
				}
				fineTune = module.getInstrument( note.instrument ).getFineTune();
			}
			if( note.effect == 0xE && ( note.parameter & 0xF0 ) == 0x50 ) {
				fineTune = ( note.parameter & 0x7 ) - ( note.parameter & 0x8 );
			}
			if( note.key > 0 ) {
				portaPeriod = micromod.Note.keyToPeriod( note.key, fineTune );
			}
			if( note.effect == 0x3 || note.effect == 0x5 ) {
				portaSpeed = note.parameter;
			} else if( note.key > 0 ) {
				period = portaPeriod;
			}
			if( note.effect == 0x1 && note.parameter > 0xF0 ) {
				trans = micromod.Note.transpose( period, note.parameter & 0xF );
				delta = ( speed > 1 ) ? ( period - trans ) * 2 / ( speed - 1 ) : 0;
				note.parameter = ( delta >> 1 ) + ( delta & 1 );
			} else if( note.effect == 0x2 && note.parameter > 0xF0 && note.parameter < 0xFE ) {
				trans = micromod.Note.transpose( period, -( note.parameter & 0xF ) );
				delta = ( speed > 1 ) ? ( trans - period ) * 2 / ( speed - 1 ) : 0;
				note.parameter = ( delta >> 1 ) + ( delta & 1 );
			} else if( note.effect == 0x3 && note.parameter > 0xF0 && note.parameter < 0xFE ) {
				if( portaPeriod < period ) {
					trans = micromod.Note.transpose( period, note.parameter & 0xF );
					delta = ( speed > 1 ) ? ( period - trans ) * 2 / ( speed - 1 ) : 0;
				} else {
					trans = micromod.Note.transpose( period, -( note.parameter & 0xF ) );
					delta = ( speed > 1 ) ? ( trans - period ) * 2 / ( speed - 1 ) : 0;
				}
				note.parameter = ( delta >> 1 ) + ( delta & 1 );
				portaSpeed = note.parameter;
			} else if( note.effect == 0xC ) {
				if( note.parameter >= 0xF0 ) {
					volume = clampVolume( volume - ( note.parameter & 0xF ) * ( volume > 24 ? 2 : 1 ) );
					note.parameter = volume;
				} else if( note.parameter >= 0xE0 ) {
					volume = clampVolume( volume + ( note.parameter & 0xF ) * ( volume > 24 ? 2 : 1 ) );
					note.parameter = volume;
				} else {
					volume = clampVolume( note.parameter );
				}
			} else if( note.effect == 0xE ) {
				if( ( note.parameter & 0xF0 ) == 0x10 ) {
					period = clampPeriod( period - ( note.parameter & 0xF ) );
				} else if( ( note.parameter & 0xF0 ) == 0x20 ) {
					period = clampPeriod( period + ( note.parameter & 0xF ) );
				} else if( ( note.parameter & 0xF0 ) == 0xA0 ) {
					volume = clampVolume( volume + ( note.parameter & 0xF ) );
				} else if( ( note.parameter & 0xF0 ) == 0xB0 ) {
					volume = clampVolume( volume - ( note.parameter & 0xF ) );
				}
			}
			pattern.setNote( row, 0, note );
		}
	}

	private static int clampVolume( int volume ) {
		if( volume < 0 ) {
			volume = 0;
		} else if( volume > 64 ) {
			volume = 64;
		}
		return volume;
	}

	private static int clampPeriod( int period ) {
		if( period < 7 ) {
			period = 6848;
		}
		return period;
	}

	private static int getNoteVolume( micromod.Module module, micromod.Pattern pattern, int rowIdx ) {
		micromod.Note note = new micromod.Note();
		pattern.getNote( rowIdx, 0, note );
		int volume = 0;
		if( note.effect == 0xC ) {
			volume = note.parameter;
		} else if( note.instrument > 0 ) {
			volume = module.getInstrument( note.instrument ).getVolume();
		}
		return volume;
	}

	private static void setNoteVolume( micromod.Pattern pattern, int rowIdx, int volume ) {
		micromod.Note note = new micromod.Note();
		pattern.getNote( rowIdx, 0, note );
		note.effect = 0xC;
		note.parameter = volume;
		pattern.setNote( rowIdx, 0, note );
	}

	private static int sqrt( int y ) {
		/* Returns 32 * sqrt(x) for x in the range 0 to 64. */
		for( int x = 0; x < 257; x++ ) {
			if( ( ( x * x ) >> 10 ) == y ) {
				return x;
			}
		}
		return 256;
	}
}
