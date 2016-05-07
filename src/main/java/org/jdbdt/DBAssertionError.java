package org.jdbdt;

/**
 * Error thrown due to a failed database assertion.
 * 
 * @since 0.1
 */
public final class DBAssertionError extends AssertionError {

  /**
   * Constructs exception with given error message.
   * @param message Error message.
   */
  public DBAssertionError(String message) {
    super(message);
  }
  
  @SuppressWarnings("javadoc")
  private static final long serialVersionUID = 1L;

}