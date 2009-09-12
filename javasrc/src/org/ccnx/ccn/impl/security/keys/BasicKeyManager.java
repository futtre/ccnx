/**
 * Part of the CCNx Java Library.
 *
 * Copyright (C) 2008, 2009 Palo Alto Research Center, Inc.
 *
 * This library is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License version 2.1
 * as published by the Free Software Foundation. 
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details. You should have received
 * a copy of the GNU Lesser General Public License along with this library;
 * if not, write to the Free Software Foundation, Inc., 51 Franklin Street,
 * Fifth Floor, Boston, MA 02110-1301 USA.
 */

package org.ccnx.ccn.impl.security.keys;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.InvalidKeyException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

import org.ccnx.ccn.CCNHandle;
import org.ccnx.ccn.KeyManager;
import org.ccnx.ccn.config.ConfigurationException;
import org.ccnx.ccn.config.SystemConfiguration;
import org.ccnx.ccn.config.UserConfiguration;
import org.ccnx.ccn.config.SystemConfiguration.DEBUGGING_FLAGS;
import org.ccnx.ccn.impl.repo.RepositoryFlowControl;
import org.ccnx.ccn.impl.security.crypto.util.MinimalCertificateGenerator;
import org.ccnx.ccn.impl.support.Log;
import org.ccnx.ccn.profiles.nameenum.EnumeratedNameList;
import org.ccnx.ccn.protocol.ContentName;
import org.ccnx.ccn.protocol.ContentObject;
import org.ccnx.ccn.protocol.KeyLocator;
import org.ccnx.ccn.protocol.KeyName;
import org.ccnx.ccn.protocol.PublisherID;
import org.ccnx.ccn.protocol.PublisherPublicKeyDigest;


public class BasicKeyManager extends KeyManager {
	
	protected KeyStore _keystore = null;
	protected String _userName = null;
	protected String _defaultAlias = null;
	protected PublisherPublicKeyDigest _defaultKeyID = null;
	protected X509Certificate _certificate = null;
	protected PrivateKey _privateKey = null;
	protected KeyLocator _keyLocator = null;
	protected boolean _initialized = false;
	
	protected KeyRepository _keyRepository = null;
	
	private char [] _password = null;
		
	public BasicKeyManager() throws ConfigurationException, IOException {
		_keyRepository = new KeyRepository();
		_userName = UserConfiguration.userName();
		// must call initialize
	}
	
	/**
	 * Separate this for the usual reasons; so subclasses can get set up before it's called.
	 * Could make fake base class constructor, and call loadKeyStore in subclass constructors,
	 * but this wouldn't work past one level, and this allows subclasses to override initialize behavior.
	 * @throws ConfigurationException 
	 */
	public synchronized void initialize() throws ConfigurationException {
		if (_initialized)
			return;
		loadKeyStore();
		_initialized = true;
	}
	
	protected boolean initialized() { return _initialized; }
	
	protected void setPassword(char [] password) {
		_password = password;
	}
	
	protected void loadKeyStore() throws ConfigurationException {
		File keyStoreFile = new File(UserConfiguration.keystoreFileName());
		if (!keyStoreFile.exists()) {
			Log.info("Creating new CCN key store..." + UserConfiguration.keystoreFileName());
			_keystore = createKeyStore();
			Log.info("...created key store.");
		}
		if (null == _keystore) {
			FileInputStream in = null;
			Log.info("Loading CCN key store from " + UserConfiguration.keystoreFileName() + "...");
			try {
				_password = UserConfiguration.keystorePassword().toCharArray();
				in = new FileInputStream(UserConfiguration.keystoreFileName());
				readKeyStore(in);
			} catch (FileNotFoundException e) {
				Log.warning("Cannot open existing key store file: " + UserConfiguration.keystoreFileName());
				throw new ConfigurationException("Cannot open existing key store file: " + UserConfiguration.keystoreFileName());
			} 
		}
		// Overriding classes must call this.
		if (!loadValuesFromKeystore(_keystore)) {
			Log.warning("Cannot process keystore!");
		}
	}
	
	/**
	 * Must have set _password.
	 * @param in
	 * @throws ConfigurationException
	 */
	protected void readKeyStore(InputStream in) throws ConfigurationException {
		if (null == _keystore) {
			try {
				Log.info("Loading CCN key store...");
				_keystore = KeyStore.getInstance(UserConfiguration.defaultKeystoreType());
				_keystore.load(in, _password);
			} catch (NoSuchAlgorithmException e) {
				Log.warning("Cannot load keystore: " + e);
				throw new ConfigurationException("Cannot load default keystore: " + e);
			} catch (CertificateException e) {
				Log.warning("Cannot load keystore with no certificates.");
				throw new ConfigurationException("Cannot load keystore with no certificates.");
			} catch (IOException e) {
				Log.warning("Cannot open existing key store: " + e);
				try {
					in.reset();
					java.io.FileOutputStream bais = new java.io.FileOutputStream("KeyDump.p12");
					byte [] tmp = new byte[2048];
					int read = in.read(tmp);
					while (read > 0) {
						bais.write(tmp, 0, read);
					}
					bais.flush();
					bais.close();
				} catch (IOException e1) {
					Log.info("Another exception: " + e1);
				}
				throw new ConfigurationException(e);
			} catch (KeyStoreException e) {
				Log.warning("Cannot create instance of preferred key store type: " + UserConfiguration.defaultKeystoreType() + " " + e.getMessage());
				Log.warningStackTrace(e);
				throw new ConfigurationException("Cannot create instance of default key store type: " + UserConfiguration.defaultKeystoreType() + " " + e.getMessage());
			} finally {
				if (null != in)
					try {
						in.close();
					} catch (IOException e) {
						Log.warning("IOException closing key store file after load.");
						Log.warningStackTrace(e);
					}
			}
		}
	}
	
	/**
	 * Read data from a newly opened, or newly created keystore.
	 * @param keyStore
	 * @return
	 * @throws ConfigurationException 
	 */
	protected boolean loadValuesFromKeystore(KeyStore keyStore) throws ConfigurationException {
		
	    _defaultAlias = UserConfiguration.defaultKeyAlias();
		KeyStore.PrivateKeyEntry entry = null;
		try {
			entry = (KeyStore.PrivateKeyEntry)_keystore.getEntry(_defaultAlias, new KeyStore.PasswordProtection(_password));
			if (null == entry) {
				Log.warning("Cannot get default key entry: " + _defaultAlias);
			}
		    _privateKey = entry.getPrivateKey();
		    _certificate = (X509Certificate)entry.getCertificate();
		    _defaultKeyID = new PublisherPublicKeyDigest(_certificate.getPublicKey());
			Log.info("Default key ID for user " + _userName + ": " + _defaultKeyID);

		    // Check to make sure we've published information about
		    // this key. (e.g. in testing, we may frequently
		    // nuke the contents of our repository even though the
		    // key remains, so need to republish). Or the first
		    // time we load this keystore, we need to publish.
		    ContentName keyName = getDefaultKeyName(_defaultKeyID.digest());
		    _keyLocator = new KeyLocator(keyName, new PublisherID(_defaultKeyID));
			Log.info("Default key locator for user " + _userName + ": " + _keyLocator);

		    if (null == getKey(_defaultKeyID, _keyLocator, KeyRepository.SHORT_KEY_TIMEOUT)) {
		    	boolean resetFlag = false;
		    	if (SystemConfiguration.checkDebugFlag(DEBUGGING_FLAGS.DEBUG_SIGNATURES)) {
		    		resetFlag = true;
		    		SystemConfiguration.setDebugFlag(DEBUGGING_FLAGS.DEBUG_SIGNATURES, false);
		    	}
		    	keyRepository().publishKey(_keyLocator.name().name(), _certificate.getPublicKey(), 
		    								_defaultKeyID, _privateKey);
		    	if (resetFlag) {
		    		SystemConfiguration.setDebugFlag(DEBUGGING_FLAGS.DEBUG_SIGNATURES, true);
		    	}
		    }
		
		} catch (Exception e) {
			generateConfigurationException("Cannot retrieve default user keystore entry.", e);
		}    
		return true;
	}
	
	synchronized protected KeyStore createKeyStore() throws ConfigurationException {
		
		File ccnDir = new File(UserConfiguration.ccnDirectory());
		if (!ccnDir.exists()) {
			if (!ccnDir.mkdirs()) {
				generateConfigurationException("Cannot create user CCN directory: " + ccnDir.getAbsolutePath(), null);
			}
		}
		
		// Alas, until 1.6, we can't set permissions on the file or directory...
		// TODO DKS when switch to 1.6, add permission settings.
		File keyStoreFile  = new File(UserConfiguration.keystoreFileName());
		if (keyStoreFile.exists())
			return null;
    	_password = UserConfiguration.keystorePassword().toCharArray();
	    FileOutputStream out = null;
		try {
			out = new FileOutputStream(UserConfiguration.keystoreFileName());
		} catch (FileNotFoundException e) {
			generateConfigurationException("Cannot create keystore file: " + UserConfiguration.keystoreFileName(), e);
		} 
	    return createKeyStore(out);	    
	}
	
	synchronized protected KeyStore createKeyStore(OutputStream out) throws ConfigurationException {

		KeyStore ks = null;
	    try {
			ks = KeyStore.getInstance(UserConfiguration.defaultKeystoreType());
			ks.load(null, _password);
		} catch (NoSuchAlgorithmException e) {
			generateConfigurationException("Cannot load empty default keystore.", e);
		} catch (CertificateException e) {
			generateConfigurationException("Cannot load empty default keystore with no certificates.", e);
		} catch (KeyStoreException e) {
			generateConfigurationException("Cannot create instance of default key store type.", e);
		} catch (IOException e) {
			generateConfigurationException("Cannot initialize instance of default key store type.", e);
		}

		KeyPairGenerator kpg = null;
		try {
			kpg = KeyPairGenerator.getInstance(UserConfiguration.defaultKeyAlgorithm());
		} catch (NoSuchAlgorithmException e) {
			generateConfigurationException("Cannot generate key using default algorithm: " + UserConfiguration.defaultKeyAlgorithm(), e);
		}
		kpg.initialize(UserConfiguration.defaultKeyLength()); 
		KeyPair userKeyPair = kpg.generateKeyPair();
		
		// Generate a self-signed certificate.
		String subjectDN = "CN=" + _userName;
		X509Certificate ssCert = null;
		try {
			 ssCert = 
				 MinimalCertificateGenerator.GenerateUserCertificate(userKeyPair, subjectDN, MinimalCertificateGenerator.MSEC_IN_YEAR);
		} catch (Exception e) {
			generateConfigurationException("InvalidKeyException generating user internal certificate.", e);
		} 

		KeyStore.PrivateKeyEntry entry =
	        new KeyStore.PrivateKeyEntry(userKeyPair.getPrivate(), new X509Certificate[]{ssCert});

	    try {
		    ks.setEntry(UserConfiguration.defaultKeyAlias(), entry, 
			        new KeyStore.PasswordProtection(_password));
	        ks.store(out, _password);
		} catch (NoSuchAlgorithmException e) {
			generateConfigurationException("Cannot save default keystore.", e);
		} catch (CertificateException e) {
			generateConfigurationException("Cannot save default keystore with no certificates.", e);
	    } catch (KeyStoreException e) {
	    	generateConfigurationException("Cannot set private key entry for user default key", e);
		} catch (IOException e) {
			generateConfigurationException("Cannot write keystore file: " + UserConfiguration.keystoreFileName(), e);
		} finally {
	        if (out != null) {
	            try {
					out.close();
				} catch (IOException e) {
					Log.warning("IOException closing key store file after load.");
					Log.warningStackTrace(e);
				}
	        }
	    }
		return ks;
	}

	static void generateConfigurationException(String message, Exception e) throws ConfigurationException {
		Log.warning(message + " " + e.getClass().getName() + ": " + e.getMessage());
		Log.warningStackTrace(e);
		throw new ConfigurationException(message, e);
	}

	public PublisherPublicKeyDigest getDefaultKeyID() {
		return _defaultKeyID;
	}

	public PublicKey getDefaultPublicKey() {
		return _certificate.getPublicKey();
	}
	
	public KeyLocator getDefaultKeyLocator() {
		return _keyLocator;
	}
	
	public KeyLocator getKeyLocator(PublisherPublicKeyDigest key) {
		if ((null == key) || (key.equals(_defaultKeyID)))
			return _keyLocator;
		ContentObject keyObject = _keyRepository.retrieve(key);
		if (null != keyObject) {
			return new KeyLocator(new KeyName(keyObject.fullName(), new PublisherID(keyObject.signedInfo().getPublisherKeyID())));
		}
		Log.info("Cannot find key locator for key: " + key);
		return null;
	}
	
	public PrivateKey getDefaultSigningKey() {
		return _privateKey;
	}
	
	/**
	 * The default key name is the publisher ID itself,
	 * under the user's key collection. 
	 * @param keyID
	 * @return
	 */
	public ContentName getDefaultKeyName(byte [] keyID) {
		ContentName keyDir =
			ContentName.fromNative(UserConfiguration.defaultUserNamespace(), 
				   			UserConfiguration.defaultKeyName());
		return new ContentName(keyDir, keyID);
	}

	public PublicKey getPublicKey(String alias) {
		Certificate cert = null;;
		try {
			cert = _keystore.getCertificate(alias);
		} catch (KeyStoreException e) {
			Log.info("No certificate for alias " + alias + " in BasicKeymManager keystore.");
			return null;
		}
		return cert.getPublicKey();
	}

	public PrivateKey getSigningKey(String alias) {
		PrivateKey key = null;;
		try {
			key = (PrivateKey)_keystore.getKey(alias, _password);
		} catch (Exception e) {
			Log.info("No key for alias " + alias + " in BasicKeymManager keystore. " + 
						e.getClass().getName() + ": " + e.getMessage());
			return null;
		}
		return key;
	}
	
	@Override
	public PrivateKey [] getSigningKeys() {
		// For now just return our default key. Eventually return multiple identity keys.
		return new PrivateKey[]{getDefaultSigningKey()};
	}
	
	/**
	 * Find the key for the given publisher, using the 
	 * available location information. Or, more generally,
	 * find a key at the given location that matches the
	 * given publisher information. If the publisher is an
	 * issuer, this gets tricky -- basically the information
	 * at the given location must be sufficient to get the
	 * right key.
	 * TODO DKS need to figure out how to decide what to do
	 * 	with a piece of content. In some sense, mime-types
	 * 	might make sense...
	 * @param publisher
	 * @param locator
	 * @return
	 * @throws IOException
	 * @throws InterruptedException 
	 */
	public PublicKey getKey(PublisherPublicKeyDigest desiredKeyID,
							KeyLocator locator,
							long timeout) throws IOException, InterruptedException {
		
		// DKS -- currently unused; contains some complex key validation behavior that
		// will move into the trust managers.
		// Otherwise, this is a name. 
		
		// First, try our local key repository.  This will go to the network if it fails.
		PublicKey key =  _keyRepository.getPublicKey(desiredKeyID, locator, timeout);		
		return key;
	}

	@Override
	public PublicKey getPublicKey(PublisherPublicKeyDigest publisher) throws IOException {
		// TODO Auto-generated method stub
		Log.finer("getPublicKey: retrieving key: " + publisher);
		
		if (_defaultKeyID.equals(publisher))
			return _certificate.getPublicKey();
		return keyRepository().getPublicKey(publisher);
	}

	@Override
	public PrivateKey getSigningKey(PublisherID publisher) {
		// TODO Auto-generated method stub
		Log.finer("getSigningKey: retrieving key: " + publisher);
		if (_defaultKeyID.equals(publisher))
			return _privateKey;
		return null;
	}

	@Override
	public PrivateKey getSigningKey(PublisherPublicKeyDigest publisher) {
		// TODO Auto-generated method stub
		Log.finer("getSigningKey: retrieving key: " + publisher);
		if (_defaultKeyID.equals(publisher))
			return _privateKey;
		return null;
	}

	@Override
	public PublicKey getPublicKey(PublisherPublicKeyDigest publisherID, KeyLocator keyLocator, long timeout) throws IOException, InterruptedException {		
		Log.finer("getPublicKey: retrieving key: " + publisherID + " located at: " + keyLocator);
		// this will try local caches, the locator itself, and if it 
		// has to, will go to the network. The result will be stored in the cache.
		// All this tells us is that the key matches the publisher. For whether
		// or not we should trust it for some reason, we have to get fancy.
		return keyRepository().getPublicKey(publisherID, keyLocator, timeout);
	}

	@Override
	public PublisherPublicKeyDigest getPublisherKeyID(PrivateKey signingKey) {
		if (_privateKey.equals(signingKey))
			return _defaultKeyID;
		return null;
	}
	
	@Override
	public KeyLocator getKeyLocator(PrivateKey signingKey) {
		if (signingKey.equals(_privateKey))
			return getDefaultKeyLocator();
		
		// DKS TODO
		return null;
	}

	@Override
	public KeyRepository keyRepository() {
		return _keyRepository;
	}

	@Override
	public void publishKey(ContentName keyName,
			PublisherPublicKeyDigest keyToPublish) throws IOException, InvalidKeyException, ConfigurationException {
		PublicKey key = null;
		if (null == keyToPublish) {
			key = getDefaultPublicKey();
		} else {
			key = getPublicKey(keyToPublish);
			if (null == key) {
				throw new InvalidKeyException("Cannot retrieive key " + keyToPublish);
			}
		}
		keyRepository().publishKey(keyName, key, getDefaultKeyID(), getDefaultSigningKey());
	}

	@Override
	public void publishKeyToRepository(ContentName keyName, 
									   PublisherPublicKeyDigest keyToPublish, 
									   CCNHandle handle) throws InvalidKeyException, IOException, ConfigurationException {
		PublicKey key = null;
		if (null == keyToPublish) {
			key = getDefaultPublicKey();
			keyToPublish = getDefaultKeyID();
		} else {
			key = getPublicKey(keyToPublish);
			if (null == key) {
				throw new InvalidKeyException("Cannot retrieive key " + keyToPublish);
			}
		}

		// HACK - want to use repo confirmation protocol to make sure data makes it to a repo
		// even if it doesn't come from us. Problem is, we may have already written it, and don't
		// want to write a brand new version of identical data. If we try to publish it under
		// the same (unversioned) name, the repository may get some of the data from the ccnd
		// cache, which will cause us to think it hasn't been written. So for the moment, we use the
		// name enumeration protocol to determine whether this key has been written to a repository
		// already.
		// This works because the last explicit name component of the key is its publisherID. 
		// We then use a further trick, just calling startWrite on the key, to get the repo
		// to read it -- not from here, but from the key server embedded in the KeyManager.
		EnumeratedNameList enl = new EnumeratedNameList(keyName.parent(), handle);
		enl.waitForData(500); // have to time out, may be nothing there.
		enl.stopEnumerating();
		if (enl.hasChildren()) {
			Log.info("Looking for children of {0} matching {1}.", keyName.parent(), keyName);
			for (ContentName name: enl.getChildren()) {
				Log.info("Child: {0}", name);
			}
		}
		if (!enl.hasChildren() || !enl.hasChild(keyName.lastComponent())) {
			// this calls startWrite, but probably shouldn't. We should call it manually.
			RepositoryFlowControl rfc = new RepositoryFlowControl(keyName, handle);
			Log.info("Key {0} published to repository.", keyName);
		} else {
			Log.info("Key {0} already published to repository, not re-publishing.", keyName);
		}
	}
	
	@Override
	public void publishKeyToRepository(CCNHandle handle) throws InvalidKeyException, IOException, ConfigurationException {
		publishKeyToRepository(_keyLocator.name().name(), null, handle);
	}

}
