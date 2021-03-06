/*
 * Copyright (C) 2006-2016 DLR, Germany
 * 
 * All rights reserved
 * 
 * http://www.rcenvironment.de/
 */

package de.rcenvironment.components.outputwriter.gui;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.swt.widgets.Display;

import de.rcenvironment.components.outputwriter.common.OutputWriterComponentConstants;
import de.rcenvironment.core.component.model.endpoint.api.EndpointDescription;
import de.rcenvironment.core.datamodel.api.DataType;
import de.rcenvironment.core.datamodel.api.EndpointActionType;
import de.rcenvironment.core.datamodel.api.EndpointType;
import de.rcenvironment.core.gui.workflow.EndpointHandlingHelper;
import de.rcenvironment.core.gui.workflow.editor.properties.EndpointEditDialog;
import de.rcenvironment.core.gui.workflow.editor.properties.EndpointSelectionPane;
import de.rcenvironment.core.gui.workflow.editor.properties.WorkflowNodeCommand;
import de.rcenvironment.core.gui.workflow.editor.properties.WorkflowNodeCommand.Executor;
import de.rcenvironment.core.utils.common.StringUtils;

/**
 * .
 * 
 * @author Sascha Zur
 */
public class OutputWriterEndpointSelectionPane extends EndpointSelectionPane {

    // The OutputLocationPane for this OutputWriter. Needed because outputLocations have to be updated for changes in the input list.
    private OutputLocationPane outputLocationPane;

    public OutputWriterEndpointSelectionPane(String title, EndpointType direction, String dynEndpointIdToManage, Executor executor) {
        super(title, direction, dynEndpointIdToManage, new String[] {}, new String[] {}, executor);
    }

    @Override
    protected void onAddClicked() {
        Set<String> paths = new TreeSet<String>();
        for (String endpointName : getDynamicEndpointNames()) {
            paths.add(getMetaData(endpointName).get(OutputWriterComponentConstants.CONFIG_KEY_FOLDERFORSAVING));
        }
        OutputWriterEndpointEditDialog dialog =
            new OutputWriterEndpointEditDialog(Display.getDefault().getActiveShell(), EndpointActionType.ADD, configuration,
                endpointType,
                dynEndpointIdToManage, false,
                endpointManager.getDynamicEndpointDefinition(dynEndpointIdToManage)
                    .getMetaDataDefinition(), new HashMap<String, String>(), paths);

        onAddClicked(dialog);
        outputLocationPane.refresh();
    }

    @Override
    protected void onEditClicked() {
        Set<String> paths = new TreeSet<String>();
        for (String endpointName : getDynamicEndpointNames()) {
            paths.add(getMetaData(endpointName).get(OutputWriterComponentConstants.CONFIG_KEY_FOLDERFORSAVING));
        }
        final String name = (String) table.getSelection()[0].getData();
        boolean isStaticEndpoint = endpointManager.getEndpointDescription(name).getEndpointDefinition().isStatic();
        EndpointDescription endpoint = endpointManager.getEndpointDescription(name);
        Map<String, String> newMetaData = cloneMetaData(endpoint.getMetaData());

        OutputWriterEndpointEditDialog dialog =
            new OutputWriterEndpointEditDialog(Display.getDefault().getActiveShell(), EndpointActionType.EDIT, configuration,
                endpointType,
                dynEndpointIdToManage, isStaticEndpoint, endpoint.getEndpointDefinition()
                    .getMetaDataDefinition(), newMetaData, paths);

        onEditClicked(name, dialog, newMetaData);
        outputLocationPane.refresh();
    }

    @Override
    protected void onEditClicked(String name, EndpointEditDialog dialog, Map<String, String> newMetaData) {

        dialog.initializeValues(name);

        if (dialog.open() == Dialog.OK) {

            EndpointDescription oldDesc = endpointManager.getEndpointDescription(name);

            String newName = dialog.getChosenName();
            DataType newType = dialog.getChosenDataType();
            newMetaData = dialog.getMetadataValues();

            if (isEndpointChanged(oldDesc, newName, newType, newMetaData)) {

                if (EndpointHandlingHelper.editEndpointDataType(endpointType, oldDesc, newType)) {
                    editEndpoint(oldDesc, newName, newType, newMetaData);
                }
            }
        }
    }

    @Override
    protected void editEndpoint(EndpointDescription oldDesc, String newName, DataType newType, Map<String, String> newMetaData) {

        String outputName = outputLocationPane.outputNameForInputName(oldDesc.getName());
        if (editConfirmed(oldDesc, newName, newType, outputName)) {
            super.editEndpoint(oldDesc, newName, newType, newMetaData);

        }
    }

    @Override
    protected void executeEditCommand(EndpointDescription oldDescription, EndpointDescription newDescription) {
        String outputName = outputLocationPane.outputIdForInputName(oldDescription.getName());
        WorkflowNodeCommand command =
            new OutputWriterEditDynamicInputCommand(endpointType, oldDescription, newDescription, outputName, this, outputLocationPane);
        execute(command);
    }

    /**
     * User has to confirm change if an outputLocation is involved.
     * 
     * @param oldDesc old endpoint
     * @param newDataType target data type
     * @param newName new name for input
     * @param outputName name of corresponding output
     * @return <code>true</code> if edit operation shall be performed <code>false</code> otherwise
     */
    protected static boolean editConfirmed(EndpointDescription oldDesc, String newName, DataType newDataType, String outputName) {
        if (outputName != null
            && (newDataType.equals(DataType.DirectoryReference) || newDataType.equals(DataType.FileReference) || newName != oldDesc
                .getName())) {

            if (!MessageDialog.openConfirm(Display.getDefault().getActiveShell(),
                Messages.editingInputWithOutputLocationDialogTitle,
                StringUtils.format(Messages.editingInputWithOutputLocationDialogText,
                    oldDesc.getName(), outputName))) {
                return false;
            }
        }
        return true;
    }

    @Override
    protected void executeRemoveCommand(List<String> names) {
        Set<String> outputLocationIds = new HashSet<String>();
        Set<String> outputLocationNames = new HashSet<String>();
        for (String inputName : names) {
            if (outputLocationPane.outputIdForInputName(inputName) != null) {
                outputLocationIds.add(outputLocationPane.outputIdForInputName(inputName));
                outputLocationNames.add(outputLocationPane.outputNameForInputName(inputName));
            }
        }
        if (deleteConfirmed(outputLocationNames)) {

            final WorkflowNodeCommand command =
                new OutputWriterRemoveDynamicInputCommand(endpointType, dynEndpointIdToManage, names,
                    new ArrayList<String>(outputLocationIds), this, outputLocationPane);
            execute(command);

        }
    }

    /**
     * User has to confirm deletion if outputLocations are involved.
     * 
     * @return <code>true</code> if edit operation shall be performed <code>false</code> otherwise
     */
    protected static boolean deleteConfirmed(Set<String> outputLocationNames) {
        if (!outputLocationNames.isEmpty()) {
            if (!MessageDialog.openConfirm(Display.getDefault().getActiveShell(),
                Messages.editingInputWithOutputLocationDialogTitle,
                StringUtils.format(Messages.deletingInputWithOutputLocationDialogText,
                    outputLocationNames.toString()))) {
                return false;
            }
        }
        return true;
    }

    private List<String> getDynamicEndpointNames() {
        List<String> result = new LinkedList<String>();
        for (EndpointDescription e : endpointManager.getDynamicEndpointDescriptions()) {
            result.add(e.getName());
        }
        return result;
    }

    protected OutputLocationPane getOutputLocationPane() {
        return outputLocationPane;
    }

    protected void setOutputLocationPane(OutputLocationPane outputLocationPane) {
        this.outputLocationPane = outputLocationPane;
    }
}
