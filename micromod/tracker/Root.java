
package micromod.tracker;

public class Root implements Element {
	private Macro parent;
	private Note sibling;
	
	public Root( Macro parent ) {
		this.parent = parent;
		sibling = new Note( parent );
	}
	
	public String getToken() {
		return "Root";
	}
	
	public Element getParent() {
		return parent;
	}
	
	public Element getSibling() {
		return sibling;
	}
	
	public Element getChild() {
		return null;
	}
	
	public void begin( String value ) {
		parent.setRoot( value );
	}
	
	public void end() {
	}
}
