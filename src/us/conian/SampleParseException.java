package us.conian;

public class SampleParseException extends RuntimeException {
	
	private static final long serialVersionUID = -4736281157699514013L;
	
	public SampleParseException() {
		super();
	}
	
	public SampleParseException(String msg) {
		super(msg);
	}
	
	public SampleParseException(Throwable cause) {
		super(cause);
	}
	
	public SampleParseException(String msg, Throwable cause) {
		super(msg, cause);
	}

}
