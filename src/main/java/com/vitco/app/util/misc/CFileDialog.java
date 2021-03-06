package com.vitco.app.util.misc;

import javax.swing.*;
import javax.swing.filechooser.FileFilter;
import java.awt.*;
import java.io.File;
import java.util.ArrayList;

/**
 * Custom file dialog
 */

public class CFileDialog extends JFileChooser {

    // list of files
    private final ArrayList<ExtensionFileFilter> accepted = new ArrayList<ExtensionFileFilter>();

    // constructor
    public CFileDialog() {
        super();
    }

    // =========================

    // add a file type
    public void addFileType(String ext) {
        accepted.add(new GeneralFilter(ext.toLowerCase(), ext.toUpperCase()));
    }

    // add a file type
    public void addFileType(String ext, String name) {
        accepted.add(new GeneralFilter(ext.toLowerCase(), name));
    }

    // add a file type
    public void addFileType(String[] exts, String name) {
        ArrayList<ExtensionFileFilter> filterList = new ArrayList<ExtensionFileFilter>();
        for (String ext : exts) {
            filterList.add(new GeneralFilter(ext, ext.toUpperCase()));
        }
        accepted.add(new CumulativeGeneralFilter(filterList, name));
    }

    // select an existing file
    public File openFile(Frame mainFrame) {
        prepare(true);
        if (showOpenDialog(mainFrame) == JFileChooser.APPROVE_OPTION) {
            File selectedfile = getSelectedFile();
            if (selectedfile.exists()) {
                return selectedfile;
            }
        }
        return null;
    }

    // select a file (doesn't need to exist)
    public File saveFile(Frame mainFrame) {
        prepare(false);
        if (showSaveDialog(mainFrame) == JFileChooser.APPROVE_OPTION) {
            // selected file
            File selectedFile = getSelectedFile();
            // currently selected ext
            String ext = getCurrentExt();
            if (ext != null) {
                // make sure filename ends correctly
                String dir = selectedFile.getPath();
                if(!dir.toLowerCase().endsWith("." + ext)) {
                    selectedFile = new File(dir + "." + ext);
                }
            }
            // dae file
            return selectedFile;
        }
        return null;
    }

    // get the current path (that is shown when this dialog is opened)
    public String getDialogPath() {
        return getCurrentDirectory().getAbsolutePath();
    }

    // set the current path (that is shown when this dialog is opened)
    public void setDialogPath(File path) {
        if (path.exists() && path.isDirectory()) {
            setCurrentDirectory(path);
        }
    }

    // =========================

    // helper - prepare this file chooser
    private void prepare(boolean allowAllFiles) {
        resetChoosableFileFilters();
        // create general file chooser that holds all file types
        if (!accepted.isEmpty()) {
            for (ExtensionFileFilter filter : accepted) {
                addChoosableFileFilter(filter);
            }
            if (allowAllFiles) {
                // create "all files" file chooser
                CumulativeGeneralFilter cumulativeGeneralFilter = new CumulativeGeneralFilter(accepted);
                setFileFilter(cumulativeGeneralFilter);
            } else {
                setFileFilter(accepted.get(0));
            }
            setAcceptAllFileFilterUsed(false);
        }
    }

    // set the title of this dialog
    public void setTitle(String title) {
        setDialogTitle(title);
    }

    // get the extension that is currently selected
    // returns null if the extension is not known
    public final String getCurrentExt() {
        FileFilter filter = this.getFileFilter();
        if (filter instanceof ExtensionFileFilter) {
            return ((ExtensionFileFilter)filter).getExt();
        }
        return null;
    }

    // helper - filter class for multiple endings
    private final class CumulativeGeneralFilter extends ExtensionFileFilter {
        private final ArrayList<ExtensionFileFilter> filters;

        private String desc = "All Files (*.*)";

        // constructor
        private CumulativeGeneralFilter(ArrayList<ExtensionFileFilter> filters) {
            this.filters = filters;
        }

        // constructor
        public CumulativeGeneralFilter(ArrayList<ExtensionFileFilter> filters, String name) {
            this(filters);
            // generate new name
            boolean first = true;
            StringBuilder builder = new StringBuilder();
            builder.append(name).append(" (");
            for (ExtensionFileFilter filter : filters) {
                builder.append(first ? "" : ", ").append("*.").append(filter.getExt());
                first = false;
            }
            builder.append(")");
            this.desc = builder.toString();
        }

        @Override
        public final String getExt() {
            File selectedFile = getSelectedFile();
            if (selectedFile != null) {
                String extension = "";
                String fileName = selectedFile.getName();
                int i = fileName.lastIndexOf('.');
                if (i > 0) {
                    extension = fileName.substring(i+1);
                }
                return extension;
            }
            return null;
        }

        @Override
        public boolean accept(File f) {
            // we want to display folders
            if (f.isDirectory()) {
                return true;
            } else {
                for (ExtensionFileFilter filter : filters) {
                    if (filter.accept(f)) {
                        return true;
                    }
                }
            }
            return false;
        }

        @Override
        public String getDescription() {
            return desc;
        }
    }

    // helper - filter class
    private final class GeneralFilter extends ExtensionFileFilter {
        private final String desc;
        private final String ext;

        private GeneralFilter(String ext, String name) {
            this.ext = ext;
            desc = name + " (*." + ext + ")";
        }

        @Override
        public final String getExt() {
            return ext;
        }

        @Override
        public boolean accept(File f) {
            // we want to display folders
            return f.isDirectory() || f.getName().endsWith("." + ext);
        }

        @Override
        public String getDescription() {
            return desc;
        }
    }

    // helper - abstract class to define the getExt() method
    private abstract class ExtensionFileFilter extends FileFilter  {
        abstract String getExt();
    }

}
