/**
Cryptix General Licence
Copyright (C) 1995, 1996, 1997, 1998, 1999, 2000 
The Cryptix Foundation Limited. All rights reserved.
Redistribution and use in source and binary forms, with or without 
modification, are permitted provided that the following conditions 
are met:
1. Redistributions of source code must retain the copyright notice, 
this list of conditions and the following disclaimer.
2. Redistributions in binary form must reproduce the above copyright 
notice, this list of conditions and the following disclaimer in 
the documentation and/or other materials provided with the 
distribution.
THIS SOFTWARE IS PROVIDED BY THE CRYPTIX FOUNDATION LIMITED ``AS IS'' 
AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, 
THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A 
PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE AUTHOR 
OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, 
SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT 
LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF 
USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED 
AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT 
LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING
IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF 
THE POSSIBILITY OF SUCH DAMAGE.
 *
 * Copyright (C) 2000 The Cryptix Foundation Limited. All rights reserved.
 *
 * Use, modification, copying and distribution of this software is subject to
 * the terms and conditions of the Cryptix General Licence. You should have
 * received a copy of the Cryptix General Licence along with this library;
 * if not, you can download a copy from http://www.cryptix.org/ .
 */
// Copied from freenet.crypt
package freenet.copied;

import java.io.IOException;
import java.io.InputStream;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.Provider;
import java.security.Security;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * @author  Jeroen C. van Gelderen (gelderen@cryptix.org)
 */
public class SHA256 {
	/** Size (in bytes) of this hash */
	private static final int HASH_SIZE = 32;

	private static final int MESSAGE_DIGESTS_TO_CACHE = 16;
	private static final ArrayList<MessageDigest> digests = new ArrayList<MessageDigest>();

	/**
	 * It won't reset the Message Digest for you!
	 * @param InputStream
	 * @param MessageDigest
	 * @return
	 * @throws IOException
	 */
	public static void hash(InputStream is, MessageDigest md) throws IOException {
		try {
			byte[] buf = new byte[4096];
			int readBytes = is.read(buf);
			while(readBytes > -1) {
				md.update(buf, 0, readBytes);
				readBytes = is.read(buf);
			}
		} finally {
            is.close();
		}
	}

	// From freenet.crypt.JceLoader
    static public final Provider SUN; // optional, may be null
    static private boolean checkUse(String prop)
    {
        return checkUse(prop, "true");
    }
    static private boolean checkUse(String prop, String def)
    {
        return "true".equalsIgnoreCase(System.getProperty("freenet.jce."+prop, def));
    }
    static {
    	SUN = checkUse("use.SUN") ? Security.getProvider("SUN") : null;
    }

    // From freenet.crypt.Util
    public static final Map<String, Provider> mdProviders;

    static private long benchmark(MessageDigest md) throws GeneralSecurityException
    {
        long times = Long.MAX_VALUE;
        byte[] input = new byte[1024];
        byte[] output = new byte[md.getDigestLength()];
        // warm-up
        for (int i = 0; i < 32; i++) {
            md.update(input, 0, input.length);
            md.digest(output, 0, output.length);
            System.arraycopy(output, 0, input, (i*output.length)%(input.length-output.length), output.length);
        }
        for (int i = 0; i < 128; i++) {
            long startTime = System.nanoTime();
            for (int j = 0; j < 4; j++) {
                for (int k = 0; k < 32; k ++) {
                    md.update(input, 0, input.length);
                }
                md.digest(output, 0, output.length);
            }
            long endTime = System.nanoTime();
            times = Math.min(endTime - startTime, times);
            System.arraycopy(output, 0, input, 0, output.length);
        }
        return times;
    }

    static {
        try {
            HashMap<String,Provider> mdProviders_internal = new HashMap<String, Provider>();

            for (String algo: new String[] {
                "SHA1", "MD5", "SHA-256", "SHA-384", "SHA-512"
            }) {
                final Provider sun = SUN;
                MessageDigest md = MessageDigest.getInstance(algo);
                md.digest();
                if (sun != null) {
                    // SUN provider is faster (in some configurations)
                    try {
                        MessageDigest sun_md = MessageDigest.getInstance(algo, sun);
                        sun_md.digest();
                        if (md.getProvider() != sun_md.getProvider()) {
                            long time_def = benchmark(md);
                            long time_sun = benchmark(sun_md);
                            System.out.println(algo + " (" + md.getProvider() + "): " + time_def + "ns");
                            System.out.println(algo + " (" + sun_md.getProvider() + "): " + time_sun + "ns");
                            if (time_sun < time_def) {
                                md = sun_md;
                            }
                        }
                    } catch(GeneralSecurityException e) {
                        // ignore
                        System.err.println(algo + "@" + sun + " benchmark failed");
                    } catch(Throwable e) {
                        // ignore
                        System.err.println(algo + "@" + sun + " benchmark failed");
                    }
                }
                Provider mdProvider = md.getProvider();
                System.out.println(algo + ": using " + mdProvider);
                mdProviders_internal.put(algo, mdProvider);
            }
            mdProviders = Collections.unmodifiableMap(mdProviders_internal);
        } catch(NoSuchAlgorithmException e) {
            // impossible
            throw new Error(e);
        }
    }

	private static final Provider mdProvider = mdProviders.get("SHA-256");

	/**
	 * Create a new SHA-256 MessageDigest
	 * Either succeed or stop the node.
	 */
	public static MessageDigest getMessageDigest() {
		try {
			MessageDigest md = null;
			synchronized(digests) {
				int x = digests.size();
				if(x == 0) md = null;
				else md = digests.remove(x-1);
			}
			if(md == null)
				md = MessageDigest.getInstance("SHA-256", mdProvider);
			return md;
		} catch(NoSuchAlgorithmException e2) {
			//TODO: maybe we should point to a HOWTO for freejvms
			System.err.println("Check your JVM settings especially the JCE!" + e2);
			e2.printStackTrace();
		}
		throw new RuntimeException();
	}

	/**
	 * Return a MessageDigest to the pool.
	 * Must be SHA-256 !
	 */
	public static void returnMessageDigest(MessageDigest md256) {
		if(md256 == null)
			return;
		String algo = md256.getAlgorithm();
		if(!(algo.equals("SHA-256") || algo.equals("SHA256")))
			throw new IllegalArgumentException("Should be SHA-256 but is " + algo);
		md256.reset();
		synchronized (digests) {
			int mdPoolSize = digests.size();
			if (mdPoolSize > MESSAGE_DIGESTS_TO_CACHE || noCache) { // don't cache too many of them
				return;
			}
			digests.add(md256);
		}
	}

	public static byte[] digest(byte[] data) {
		MessageDigest md = getMessageDigest();
		byte[] hash = md.digest(data);
		returnMessageDigest(md);
		return hash;
	}

	public static int getDigestLength() {
		return HASH_SIZE;
	}
	
	private static boolean noCache = false;
	
}
