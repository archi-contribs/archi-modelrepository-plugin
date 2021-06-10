/**
 * This program and the accompanying materials
 * are made available under the terms of the License
 * which accompanies this distribution in the file LICENSE.txt
 */
package org.archicontribs.modelrepository.authentication;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.security.GeneralSecurityException;

import org.archicontribs.modelrepository.ModelRepositoryPlugin;
import org.archicontribs.modelrepository.grafico.GraficoUtils;
import org.archicontribs.modelrepository.grafico.IGraficoConstants;
import org.archicontribs.modelrepository.preferences.IPreferenceConstants;
import org.eclipse.core.runtime.ILog;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.Status;
import org.eclipse.jgit.api.TransportConfigCallback;
import org.eclipse.jgit.transport.JschConfigSessionFactory;
import org.eclipse.jgit.transport.OpenSshConfig;
import org.eclipse.jgit.transport.SshSessionFactory;
import org.eclipse.jgit.transport.SshTransport;
import org.eclipse.jgit.transport.Transport;
import org.eclipse.jgit.transport.TransportHttp;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.eclipse.jgit.util.FS;
import org.eclipse.osgi.util.NLS;
import org.osgi.framework.Bundle;
import org.osgi.framework.FrameworkUtil;

import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;


/**
 * Authenticator for SSH and HTTP
 * 
 * @author Phillip Beauvoir
 */
public final class CredentialsAuthenticator {
	private static final Bundle BUNDLE = FrameworkUtil.getBundle(CredentialsAuthenticator.class);
	private static final ILog LOGGER = Platform.getLog(BUNDLE);
    
    public interface SSHIdentityProvider {
        File getIdentityFile() throws IOException;
        char[] getIdentityPassword() throws IOException, GeneralSecurityException;
    }
    
    /**
     * SSH Identity Provider. Default is with details from Prefs
     */
    private static SSHIdentityProvider sshIdentityProvider = new SSHIdentityProvider() {
        @Override
        public File getIdentityFile() throws IOException {
            File file = new File(ModelRepositoryPlugin.INSTANCE.getPreferenceStore().getString(IPreferenceConstants.PREFS_SSH_IDENTITY_FILE));
            
            if(!file.exists()) {
                throw new IOException(NLS.bind(Messages.CredentialsAuthenticator_0, file));
            }
            
            return file;
        }
        
        @Override
        public char[] getIdentityPassword() throws IOException, GeneralSecurityException {
            char[] password = null;
            
            if(ModelRepositoryPlugin.INSTANCE.getPreferenceStore().getBoolean(IPreferenceConstants.PREFS_SSH_IDENTITY_REQUIRES_PASSWORD)) {
                EncryptedCredentialsStorage cs = new EncryptedCredentialsStorage(
                        new File(ModelRepositoryPlugin.INSTANCE.getUserModelRepositoryFolder(), IGraficoConstants.SSH_CREDENTIALS_FILE));

                if(cs.hasCredentialsFile()) {
                    password = cs.getPassword();
                }
                else {
                    throw new IOException(Messages.CredentialsAuthenticator_1);
                }
            }
            
            return password;
        }
    };
    
    public static void setSSHIdentityProvider(SSHIdentityProvider sshIdentityProvider) {
        CredentialsAuthenticator.sshIdentityProvider = sshIdentityProvider;
    }
    
    /**
     * Factory method to get the default TransportConfigCallback for authentication for repoURL
     * npw can be null and is ignored if repoURL is SSH
     */
    public static TransportConfigCallback getTransportConfigCallback(final String repoURL, final UsernamePassword npw) throws IOException {
        // SSH
        if(GraficoUtils.isSSH(repoURL)) {
            return new TransportConfigCallback() {
                @Override
                public void configure(Transport transport) {
                    transport.setRemoveDeletedRefs(true); // Delete remote branches that we don't have
                    
                    if(transport instanceof SshTransport) {
                        ((SshTransport)transport).setSshSessionFactory(getSshSessionFactory());
                    }
                }
                
                protected SshSessionFactory getSshSessionFactory() {
                    return new JschConfigSessionFactory() {

                        @Override
                        protected void configure(OpenSshConfig.Host host, Session session) {
                            session.setConfig("StrictHostKeyChecking", "no"); //$NON-NLS-1$ //$NON-NLS-2$
                        }

                        @Override
                        protected JSch createDefaultJSch(FS fs) throws JSchException {
                            JSch jsch = super.createDefaultJSch(fs);
                            
                            // TODO - we might not need to do this as it sets default locations for rsa_pub
                            jsch.removeAllIdentity();
                            
                            File file = null;
                            char[] pw = null;
                            try {
                                file = sshIdentityProvider.getIdentityFile();
                                pw = sshIdentityProvider.getIdentityPassword();
                            }
                            catch(IOException | GeneralSecurityException ex) {
                                throw new JSchException(ex.getMessage());
                            }
                            
                            if(pw != null) {
                                jsch.addIdentity(file.getAbsolutePath(), new String(pw));
                            }
                            else {
                                jsch.addIdentity(file.getAbsolutePath());
                            }
                            
                            return jsch;
                        }
                    };
                }
            };
        }
        
        // HTTP
        if(npw != null) {
            return new TransportConfigCallback() {
                @Override
                public void configure(Transport transport) {
                    transport.setCredentialsProvider(new UsernamePasswordCredentialsProvider(npw.getUsername(), npw.getPassword()));                 
                    // check for environment variable to add additional http headers to git request
                    Map<String,String> envVariables = System.getenv();
                    String additionalHeader = envVariables.get(ModelRepositoryPlugin.ENV_VAR_ADDITIONALHEADER);
                    if (additionalHeader != null && transport instanceof TransportHttp) {
                    	TransportHttp transportHttp = (TransportHttp) transport;
                        Map<String, String> headerMap = new HashMap<String, String>(1);
                        String headerKeyValue[] = additionalHeader.split(":");
                        if (headerKeyValue.length==2) {
                            headerMap.put(headerKeyValue[0], headerKeyValue[1]);
                            transportHttp.setAdditionalHeaders(headerMap);
                            log(">> added http headers:" + headerMap);
                        }
                    }
                    transport.setRemoveDeletedRefs(true); // Delete remote branches that we don't have
                };
            };
        }
        
        throw new IOException(Messages.CredentialsAuthenticator_2 + " " + repoURL); //$NON-NLS-1$
    }
    
    public static void log(String msg) {
        log(msg, null);
     }

     public static void log(String msg, Exception e) {
        LOGGER.log(new Status((e==null? Status.INFO:Status.ERROR), BUNDLE.getSymbolicName(), msg, e));
     }
}
