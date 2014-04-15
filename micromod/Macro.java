
package micromod;

public class Macro {
	private Scale scale;
	private int rootKey;
	private Pattern notes;
	
	public Macro( String scale, String rootKey, Pattern notes ) {
		this.scale = new Scale( scale != null ? scale : Scale.CHROMATIC );
		this.rootKey = Note.parseKey( rootKey != null ? rootKey : "C-2" );
		this.notes = new Pattern( 1, notes );
	}

	/* Return a new Macro with the notes transposed to the specified key.*/
	public Macro transpose( String key ) {
		int semitones = Note.parseKey( key ) - rootKey;
		Pattern pattern = new Pattern( 1, notes );
		Note note = new Note();
		for( int rowIdx = 0; rowIdx < 64; rowIdx++ ) {
			pattern.getNote( rowIdx, 0, note );
			note.transpose( semitones, 64, null );
			pattern.setNote( rowIdx, 0, note );
		}
		return new Macro( scale.transpose( semitones ), key, pattern );
	}

	/* Expand macro into the specified pattern until end or an instrument is set. */
	/* When the end of the pattern is reached, macro expansion continues into the */
	/* pattern specified by patternIdx2 unless a negative index is specified. */
	public void expand( Module module, int patternIdx, int channelIdx, int rowIdx, int patternIdx2 ) {
		int macroRowIdx = 0, srcKey = 0, dstKey = 0, distance = 0, volume = 64;
		Pattern pattern = module.getPattern( patternIdx );
		Note note = new Note();
		while( macroRowIdx < Pattern.NUM_ROWS ) {
			if( rowIdx >= Pattern.NUM_ROWS ) {
				if( patternIdx2 < 0 ) {
					break;
				}
				pattern = module.getPattern( patternIdx2 );
				rowIdx = 0;
			}
			pattern.getNote( rowIdx, channelIdx, note );
			if( note.instrument > 0 ) {
				break;
			}
			if( note.key > 0 ) {
				distance = scale.getDistance( rootKey, note.key );
			}
			int effect = note.effect;
			int param = note.parameter;
			notes.getNote( macroRowIdx++, 0, note );
			if( note.key > 0 ) {
				srcKey = note.key;
				dstKey = scale.transpose( srcKey, distance );
			}
			int semitones = 0;
			if( dstKey > 0 ) {
				semitones = dstKey - srcKey;
			}
			if( effect == 0xC ) {
				volume = param;
			} else if( ( note.effect | note.parameter ) == 0 ) {
				note.effect = effect;
				note.parameter = param;
			}
			note.transpose( semitones, volume, module );
			if( semitones != 0 && note.effect == 0 && note.parameter != 0 ) {
				/* Adjust arpeggio.*/
				int dist = scale.getDistance( srcKey, srcKey + ( ( note.parameter >> 4 ) & 0xF ) );
				int arp1 = scale.transpose( dstKey, dist ) - dstKey;
				if( arp1 < 0 ) arp1 = 0;
				if( arp1 > 15 ) arp1 = ( arp1 - 3 ) % 12 + 3;
				dist = scale.getDistance( srcKey, srcKey + ( note.parameter & 0xF ) );
				int arp2 = scale.transpose( dstKey, dist ) - dstKey;
				if( arp2 < 0 ) arp2 = 0;
				if( arp2 > 15 ) arp2 = ( arp2 - 3 ) % 12 + 3;
				note.parameter = ( arp1 << 4 ) + ( arp2 & 0xF );
			}
			pattern.setNote( rowIdx++, channelIdx, note );
		}
	}
}
