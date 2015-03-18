package com.theonelab.navi.gypsum;

import java.util.UUID;

/**
 * Simple interface that simply exists to contain various extra bits of
 * information about the Gypsum protocol.
 *
 * Things like the RFCOMM service UUID, Protocol version, and other ancilliary
 * information live here.
 */
public interface GypsumProtocol {
  /** The version number for the Gypsum protocol that Gypsum the application implements. */
  public static final int PROTOCOL_VERSION = 1;

  /** Human-readable service name used in the RFCOMM SDP record. */
  public static final String BT_SERVICE_NAME = "Gypsum";

  /** UUID used for the RFCOMM SDP record. */
  public static final UUID BT_SERVICE_UUID =
      UUID.fromString("199d6fc0-adcb-11e4-a32c-6c4008a5fbd2");
}
