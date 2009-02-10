package test.ccn.data.security;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Security;
import java.security.cert.X509Certificate;
import java.sql.Timestamp;
import java.util.Arrays;
import java.util.Date;

import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.junit.BeforeClass;
import org.junit.Test;

import test.ccn.data.XMLEncodableTester;

import com.parc.ccn.data.ContentName;
import com.parc.ccn.data.security.SignedInfo;
import com.parc.ccn.data.security.KeyLocator;
import com.parc.ccn.data.security.PublisherKeyID;
import com.parc.security.crypto.certificates.BCX509CertificateGenerator;

public class SignedInfoTest {

	static final String baseName = "test";
	static final String subName2 = "smetters";
	static final String document2 = "test2.txt";	
	static public byte [] document3 = new byte[]{0x01, 0x02, 0x03, 0x04,
				0x05, 0x06, 0x07, 0x08, 0x09, 0x0a, 0x0b, 0x0c,
				0x0d, 0x0e, 0x0f, 0x1f, 0x1b, 0x1c, 0x1d, 0x1e,
				0x1f, 0x2e, 0x3c, 0x4a, 0x5c, 0x6d, 0x7e, 0xf};

	static ContentName name = null;

	static final String rootDN = "C=US,O=Organization,OU=Organizational Unit,CN=Issuer";
	static final String endDN = "C=US,O=Final Org,L=Locality,CN=Fred Jones,E=fred@final.org";
	static final Date start = new Date(); 
	static final Date end = new Date(start.getTime() + (60*60*24*365));
	static final  String keydoc = "key";	
	static ContentName keyname = null;

	static KeyPair pair = null;
	static X509Certificate cert = null;
	static KeyLocator nameLoc = null;
	static KeyLocator keyLoc = null;
	static KeyLocator certLoc = null;
	
	static public byte [] signature = new byte[256];
	static public byte [] publisherid = new byte[32];
	static PublisherKeyID pubkey = null;	
	
	@BeforeClass
	public static void setUpBeforeClass() throws Exception {
		try {
			name = ContentName.fromURI(new String[]{baseName, subName2, document2});
			keyname = ContentName.fromURI(new String[]{baseName, subName2, keydoc});
			Security.addProvider(new BouncyCastleProvider());
			
			// generate key pair
			KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
			kpg.initialize(512); // go for fast
			pair = kpg.generateKeyPair();
			cert = 
				BCX509CertificateGenerator.GenerateX509Certificate(
					pair.getPublic(),
					rootDN,
					endDN,
					null,
					start,
					end,
					null,
					pair.getPrivate(),
					null);
			nameLoc = new KeyLocator(keyname);
			keyLoc = new KeyLocator(pair.getPublic());
			certLoc = new KeyLocator(cert);
			
			Arrays.fill(signature, (byte)1);
			Arrays.fill(publisherid, (byte)3);
			
			pubkey = new PublisherKeyID(publisherid);
		} catch (Exception ex) {
			XMLEncodableTester.handleException(ex);
			System.out.println("Unable To Initialize Test!!!");
		}	
	}

	@Test
	public void testDecodeInputStream() {
		SignedInfo nca = new SignedInfo(
				pubkey, 
				new Timestamp(System.currentTimeMillis()), 
				SignedInfo.ContentType.LEAF, 
				nameLoc);
		SignedInfo dnca = new SignedInfo();
		SignedInfo bdnca = new SignedInfo();
		XMLEncodableTester.encodeDecodeTest("SignedInfo(name)", nca, dnca, bdnca);

		SignedInfo kca = new SignedInfo(
				pubkey, 
				new Timestamp(System.currentTimeMillis()), 
				SignedInfo.ContentType.LEAF, 
				keyLoc);
		SignedInfo dkca = new SignedInfo();
		SignedInfo bdkca = new SignedInfo();
		XMLEncodableTester.encodeDecodeTest("SignedInfo(key)", kca, dkca, bdkca);

		SignedInfo cca = new SignedInfo(pubkey, 
				new Timestamp(System.currentTimeMillis()), 
				SignedInfo.ContentType.LEAF, 
				certLoc);
		SignedInfo dcca = new SignedInfo();
		SignedInfo bdcca = new SignedInfo();
		XMLEncodableTester.encodeDecodeTest("SignedInfo(cert)", cca, dcca, bdcca);
		
	}

}