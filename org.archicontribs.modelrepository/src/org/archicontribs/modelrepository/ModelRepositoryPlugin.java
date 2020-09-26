/**
 * This program and the accompanying materials
 * are made available under the terms of the License
 * which accompanies this distribution in the file LICENSE.txt
 */
package org.archicontribs.modelrepository;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.io.IOException;
import java.net.URL;

import org.archicontribs.modelrepository.authentication.ProxyAuthenticator;
import org.archicontribs.modelrepository.grafico.ArchiRepository;
import org.archicontribs.modelrepository.grafico.GraficoUtils;
import org.archicontribs.modelrepository.grafico.IArchiRepository;
import org.archicontribs.modelrepository.grafico.IRepositoryListener;
import org.archicontribs.modelrepository.grafico.RepositoryListenerManager;
import org.archicontribs.modelrepository.preferences.IPreferenceConstants;
import org.eclipse.core.runtime.FileLocator;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.jgit.transport.UserAgent;
import org.eclipse.ui.plugin.AbstractUIPlugin;
import org.osgi.framework.BundleContext;

import com.archimatetool.editor.model.IEditorModelManager;
import com.archimatetool.editor.utils.StringUtils;
import com.archimatetool.model.IArchimateModel;



/**
 * Activitor
 * 
 * @author Phillip Beauvoir
 */
public class ModelRepositoryPlugin extends AbstractUIPlugin implements PropertyChangeListener {

    public static final String PLUGIN_ID = "org.archicontribs.modelrepository"; //$NON-NLS-1$
    
    /**
     * The shared instance
     */
    public static ModelRepositoryPlugin INSTANCE;
    
    public static String ENV_VAR_USERAGENT = "ARCHI_GIT_USERAGENT";
    public static String ENV_VAR_ADDITIONALHEADER = "ARCHI_GIT_ADDITIONALHEADER";
    
    public ModelRepositoryPlugin() {
        INSTANCE = this;
    }

    @Override
    public void start(BundleContext context) throws Exception {
        super.start(context);
        IEditorModelManager.INSTANCE.addPropertyChangeListener(this);
        //override git useragent if specified in system property
        if (System.getenv(ENV_VAR_USERAGENT) != null && !System.getenv(ENV_VAR_USERAGENT).isEmpty()) {
        	UserAgent.set(System.getenv(ENV_VAR_USERAGENT));
        }
        // Set this first
        ProxyAuthenticator.init();
    }
    
    @Override
    public void stop(BundleContext context) throws Exception {
        IEditorModelManager.INSTANCE.removePropertyChangeListener(this);
        super.stop(context);
    }
    
    /**
     * @return The File Location of this plugin
     */
    public File getPluginFolder() {
        URL url = getBundle().getEntry("/"); //$NON-NLS-1$
        try {
            url = FileLocator.resolve(url);
        }
        catch(IOException ex) {
            ex.printStackTrace();
        }
        
        return new File(url.getPath());
    }
    
    /**
     * @return The folder where we store repositories
     */
    public File getUserModelRepositoryFolder() {
        // Get from preferences
        String path = getPreferenceStore().getString(IPreferenceConstants.PREFS_REPOSITORY_FOLDER);
        
        if(StringUtils.isSet(path)) {
            File file = new File(path);
            if(file.canWrite()) {
                return file;
            }
        }
        
        // Default
        path = getPreferenceStore().getDefaultString(IPreferenceConstants.PREFS_REPOSITORY_FOLDER);
        return new File(path);
    }
    
    @Override
    public void propertyChange(PropertyChangeEvent evt) {
        // Notify on Save
        if(evt.getPropertyName().equals(IEditorModelManager.PROPERTY_MODEL_SAVED)) {
            IArchimateModel model = (IArchimateModel)evt.getNewValue();
            if(GraficoUtils.isModelInLocalRepository(model)) {
                IArchiRepository repo = new ArchiRepository(GraficoUtils.getLocalRepositoryFolderForModel(model));
                RepositoryListenerManager.INSTANCE.fireRepositoryChangedEvent(IRepositoryListener.REPOSITORY_CHANGED, repo);
            }
        }
    }
    
    public void log(int severity, String message, Throwable ex) {
        getLog().log(
                new Status(severity, INSTANCE.getBundle().getSymbolicName(), IStatus.OK, message, ex));
    }
}
