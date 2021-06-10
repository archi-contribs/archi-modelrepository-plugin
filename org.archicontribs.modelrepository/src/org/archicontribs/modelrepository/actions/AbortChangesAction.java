/**
 * This program and the accompanying materials
 * are made available under the terms of the License
 * which accompanies this distribution in the file LICENSE.txt
 */
package org.archicontribs.modelrepository.actions;

import java.io.IOException;

import org.archicontribs.modelrepository.IModelRepositoryImages;
import org.archicontribs.modelrepository.grafico.ArchiRepository;
import org.archicontribs.modelrepository.grafico.GraficoModelLoader;
import org.archicontribs.modelrepository.grafico.GraficoUtils;
import org.archicontribs.modelrepository.grafico.IGraficoConstants;
import org.archicontribs.modelrepository.grafico.IRepositoryListener;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.ui.IWorkbenchWindow;

import com.archimatetool.editor.model.IEditorModelManager;
import com.archimatetool.model.IArchimateModel;

/**
 * Abort Unpublished Changes Action
 * 
 * @author Phillip Beauvoir
 */
public class AbortChangesAction extends AbstractModelAction {
    
    public AbortChangesAction(IWorkbenchWindow window) {
        super(window);
        setImageDescriptor(IModelRepositoryImages.ImageFactory.getImageDescriptor(IModelRepositoryImages.ICON_ABORT));
        setText(Messages.AbortChangesAction_0);
        setToolTipText(Messages.AbortChangesAction_0);
    }

    public AbortChangesAction(IWorkbenchWindow window, IArchimateModel model) {
        this(window);
        if(model != null) {
            setRepository(new ArchiRepository(GraficoUtils.getLocalRepositoryFolderForModel(model)));
        }
    }

    @Override
    public void run() {
        boolean response = MessageDialog.openConfirm(fWindow.getShell(),
                Messages.AbortChangesAction_0,
                Messages.AbortChangesAction_1);
        if(!response) {
            return;
        }
        
        // Must save the model now because the GraficoModelLoader will close it.
        // If the user cancels close, then the model will be re-opened twice
        IArchimateModel model = getRepository().locateModel();
        if(model != null && IEditorModelManager.INSTANCE.isModelDirty(model)) {
            try {
                IEditorModelManager.INSTANCE.saveModel(model);
            }
            catch(IOException ex) {
                displayErrorDialog(Messages.AbortChangesAction_0, ex);
                return;
            }
        }
        
        try {
            getRepository().resetToRef(IGraficoConstants.HEAD);
        }
        catch(Exception ex) {
            displayErrorDialog(Messages.AbortChangesAction_0, ex);
        }
        
        try {
            // Load the model
            new GraficoModelLoader(getRepository()).loadModel();
            
            // Save the checksum
            getRepository().saveChecksum();
        }
        catch(IOException ex) {
            displayErrorDialog(Messages.AbortChangesAction_0, ex);
        }
        
        notifyChangeListeners(IRepositoryListener.HISTORY_CHANGED);
    }
}
