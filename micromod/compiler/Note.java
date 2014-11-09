
package micromod.compiler;

public class Note implements Envelope {
	private Macro parent;
	private Repeat sibling;
	private Attack child = new Attack( this, new Decay( this, new TimeStretch( this ) ) );
	private micromod.Note note = new micromod.Note();
	private int attackRows, decayRows, timeStretchRows;

	public Note( Macro parent ) {
		this.parent = parent;
		sibling = new Repeat( parent );
	}
	
	public String getToken() {
		return "Note";
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
		attackRows = decayRows = timeStretchRows = 0;
		note.fromString( value );
		if( note.effect == 0xC && note.parameter > 0x40 ) {
			/* Apply x^2 volume-curve for effect C41 to C4F. */
			int vol = ( note.parameter & 0xF ) + 1;
			note.parameter = ( vol * vol ) >> 2;
		}
	}

	public void end() {
		parent.nextNote( note, attackRows, decayRows, timeStretchRows );
	}

	public void setTimeStretch( int rows ) {
		timeStretchRows = rows;
	}

	public void setAttack( int rows ) {
		attackRows = rows;
	}

	public void setDecay( int rows ) {	
		decayRows = rows;
	}
}
