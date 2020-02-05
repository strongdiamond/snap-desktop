package org.esa.snap.rcp.colormanip;

import org.esa.snap.core.datamodel.ColorPaletteDef;
import org.esa.snap.core.datamodel.ColorSchemeInfo;
import org.esa.snap.core.datamodel.ColorSchemeDefaults;
import org.esa.snap.core.util.PropertyMap;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.swing.*;
import javax.swing.plaf.basic.BasicComboBoxRenderer;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.awt.*;
import java.io.*;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

import static org.esa.snap.core.datamodel.ColorSchemeDefaults.*;

/**
 * Manages all the color schemes
 *
 * @author Daniel Knowles (NASA)
 * @date Jan 2020
 */
public class ColorSchemeManager {

    public boolean isjComboBoxShouldFire() {
        return jComboBoxShouldFire;
    }

    public void setjComboBoxShouldFire(boolean jComboBoxShouldFire) {
        this.jComboBoxShouldFire = jComboBoxShouldFire;
    }


    private ArrayList<ColorSchemeInfo> colorSchemeInfos = new ArrayList<ColorSchemeInfo>();
    private ArrayList<ColorSchemeInfo> colorSchemeSortedInfos = new ArrayList<ColorSchemeInfo>();
    private ArrayList<ColorSchemeInfo> colorSchemeSortedVerboseInfos = new ArrayList<ColorSchemeInfo>();
    private ArrayList<ColorSchemeInfo> colorSchemeLutInfos = new ArrayList<ColorSchemeInfo>();

    private File colorSchemesFile = null;
    private File colorSchemeLutFile = null;

    private JComboBox jComboBox = null;
    private boolean jComboBoxShouldFire = true;

    private final String STANDARD_SCHEME_COMBO_BOX_FIRST_ENTRY_NAME = "-- none --";
    private String jComboBoxFirstEntryName = null;
    private ColorSchemeInfo jComboBoxFirstEntryColorSchemeInfo = null;

    private File colorPaletteAuxDir = null;
    private File colorSchemesAuxDir = null;

    private boolean verbose = true;
    private boolean showDisabled = false;
    private boolean sortComboBox = false;

    private boolean initialized = false;

    private ColorSchemeInfo currentSelection = null;


    static ColorSchemeManager manager = new ColorSchemeManager();

    public static ColorSchemeManager getDefault() {
        return manager;
    }


    public ColorSchemeManager() {
        init();
    }


    public void init() {

        if (!initialized) {
            Path getColorSchemesAuxDir = ColorSchemeUtils.getDirNameColorSchemes();
            if (getColorSchemesAuxDir != null) {
                this.colorSchemesAuxDir = getColorSchemesAuxDir.toFile();
                if (!colorSchemesAuxDir.exists()) {
                    return;
                }
            } else {
                return;
            }

            Path getColorPalettesAuxDir = ColorSchemeUtils.getDirNameColorPalettes();
            if (getColorPalettesAuxDir != null) {
                this.colorPaletteAuxDir = getColorPalettesAuxDir.toFile();
                if (!colorPaletteAuxDir.exists()) {
                    return;
                }
            } else {
                return;
            }

            colorSchemesFile = new File(this.colorSchemesAuxDir, ColorSchemeDefaults.COLOR_SCHEMES_FILENAME);
            colorSchemeLutFile = new File(this.colorSchemesAuxDir, ColorSchemeDefaults.COLOR_SCHEME_LUT_FILENAME);

            if (colorSchemesFile.exists() && colorSchemeLutFile.exists()) {
                initColorSchemeInfos();
                createSortedVerboseInfos();
                createSortedInfos();

                initComboBox();
                initColorSchemeLut();

                reset();
            }

            initialized = true;
        }
    }



//    private void numericalSort() {
//        Collections.sort(colorSchemeInfos, new Comparator<ColorSchemeInfo>() {
//            @Override
//            public int compare(ColorSchemeInfo p1, ColorSchemeInfo p2) {
//                return p1.getEntryNumber() - p2.getEntryNumber(); // Ascending
//            }
//        });
//    }

    private void createSortedInfos() {

        for (ColorSchemeInfo colorSchemeInfo : colorSchemeInfos) {
            colorSchemeSortedInfos.add(colorSchemeInfo);
        }

        Collections.sort(colorSchemeSortedInfos, new Comparator<ColorSchemeInfo>() {
            public int compare(ColorSchemeInfo s1, ColorSchemeInfo s2) {
                return s1.toString().compareToIgnoreCase(s2.toString());
            }
        });
    }

    private void createSortedVerboseInfos() {

        String NULL_VALUE = "zzzzz";

        for (ColorSchemeInfo colorSchemeInfo : colorSchemeInfos) {
            colorSchemeSortedVerboseInfos.add(colorSchemeInfo);
        }

        Collections.sort(colorSchemeSortedVerboseInfos, new Comparator<ColorSchemeInfo>() {

            public int compare(ColorSchemeInfo o1, ColorSchemeInfo o2) {

                String x1 = o1.getDisplayName();
                String x2 = o2.getDisplayName();

                if (x1 == null) { x1 = NULL_VALUE;}
                if (x2 == null) { x2 = NULL_VALUE;}
                int sComp = x1.compareToIgnoreCase(x2);

                if (sComp != 0) {
                    return sComp;
                }

                String y1 = o1.getName();
                String y2 = o2.getName();
                if (y1 == null) { y1 = NULL_VALUE;}
                if (y1 == null) { y1 = NULL_VALUE;}

                return y1.compareToIgnoreCase(y2);
            }
        });
    }


    public void checkPreferences(PropertyMap configuration) {

        boolean updateSchemeSelector = false;

        boolean useDisplayName = configuration.getPropertyBool(PROPERTY_SCHEME_VERBOSE_KEY, PROPERTY_SCHEME_VERBOSE_DEFAULT);
        if (isVerbose() != useDisplayName) {
            setVerbose(useDisplayName);
            updateSchemeSelector = true;
        }

        boolean sortComboBox = configuration.getPropertyBool(PROPERTY_SCHEME_SORT_KEY, PROPERTY_SCHEME_SORT_DEFAULT);
        if (isSortComboBox() != sortComboBox) {
            setSortComboBox(sortComboBox);
            updateSchemeSelector = true;
        }

        boolean showDisabled = configuration.getPropertyBool(PROPERTY_SCHEME_SHOW_DISABLED_KEY, PROPERTY_SCHEME_SHOW_DISABLED_DEFAULT);
        if (isShowDisabled() != showDisabled) {
            setShowDisabled(showDisabled);
            updateSchemeSelector = true;
        }

        if (updateSchemeSelector) {
            refreshComboBox();
        }


        ColorSchemeManager.getDefault().validateSelection();
    }


    private boolean isShowDisabled() {
        return showDisabled;
    }

    private void setShowDisabled(boolean showDisabled) {
        if (this.showDisabled != showDisabled) {
            this.showDisabled = showDisabled;
        }
    }


    private boolean isSortComboBox() {
        return sortComboBox;
    }


    private void setSortComboBox(boolean sortComboBox) {
        if (this.sortComboBox != sortComboBox) {
            this.sortComboBox = sortComboBox;
        }
    }


    private void refreshComboBox() {
        ColorSchemeInfo selectedColorSchemeInfo = (ColorSchemeInfo) jComboBox.getSelectedItem();
        if (selectedColorSchemeInfo != null && selectedColorSchemeInfo.isEnabled()) {
            currentSelection = selectedColorSchemeInfo;
        }
        jComboBox.removeAllItems();
        populateComboBox();
        if (currentSelection != null) {
            jComboBox.setSelectedItem(currentSelection);
        }
        jComboBox.repaint();
    }


    // If user selects an invalid scheme then use the current selection, otherwise update to the selected scheme
    private void validateSelection() {
        if (jComboBox != null) {
            ColorSchemeInfo selectedColorSchemeInfo = (ColorSchemeInfo) jComboBox.getSelectedItem();
            if (selectedColorSchemeInfo != null && selectedColorSchemeInfo != currentSelection) {
                if (selectedColorSchemeInfo.isEnabled()) {
                    currentSelection = selectedColorSchemeInfo;
                } else if (currentSelection != null) {
                    jComboBox.setSelectedItem(currentSelection);
                }
            }
        }
    }

    private void populateComboBox() {

        ArrayList<ColorSchemeInfo> colorSchemeCurrentInfos = null;

        if (isSortComboBox()) {
            if (isVerbose()) {
//                colorSchemeCurrentInfos = colorSchemeSortedVerboseInfos;
                colorSchemeCurrentInfos = colorSchemeSortedInfos;

            } else {
                colorSchemeCurrentInfos = colorSchemeSortedInfos;
            }
        } else {
            colorSchemeCurrentInfos = colorSchemeInfos;
        }

        final String[] toolTipsArray = new String[colorSchemeCurrentInfos.size()];
        final Boolean[] enabledArray = new Boolean[colorSchemeCurrentInfos.size()];

        int i = 0;
        for (ColorSchemeInfo colorSchemeInfo : colorSchemeCurrentInfos) {
            if (colorSchemeInfo.isEnabled() || (!colorSchemeInfo.isEnabled() && showDisabled)) {
                toolTipsArray[i] = colorSchemeInfo.getDescription();
                enabledArray[i] = colorSchemeInfo.isEnabled();
                jComboBox.addItem(colorSchemeInfo);
                i++;
            }
        }

        final MyComboBoxRenderer myComboBoxRenderer = new MyComboBoxRenderer();
        myComboBoxRenderer.setTooltipList(toolTipsArray);
        myComboBoxRenderer.setEnabledList(enabledArray);
        jComboBox.setRenderer(myComboBoxRenderer);
    }


    private void initComboBox() {

        jComboBox = new JComboBox();
        jComboBox.setEditable(false);
        if (colorSchemeLutFile != null) {
            jComboBox.setToolTipText("To modify see file: " + colorSchemesAuxDir + "/" + colorSchemeLutFile.getName());
        }

        populateComboBox();
    }


    private boolean initColorSchemeInfos() {

        setjComboBoxFirstEntryName(STANDARD_SCHEME_COMBO_BOX_FIRST_ENTRY_NAME);
        jComboBoxFirstEntryColorSchemeInfo = new ColorSchemeInfo(getjComboBoxFirstEntryName(), 0, getjComboBoxFirstEntryName(), null, null, null, 0, 0, false, true, true, null, null, null, colorPaletteAuxDir);
        colorSchemeInfos.add(jComboBoxFirstEntryColorSchemeInfo);

        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        Document dom = null;

        try {
            DocumentBuilder db = dbf.newDocumentBuilder();
            dom = db.parse(new FileInputStream(colorSchemesFile));
        } catch (ParserConfigurationException pce) {
            pce.printStackTrace();
        } catch (SAXException se) {
            se.printStackTrace();
        } catch (IOException ioe) {
            ioe.printStackTrace();
        }


        Element rootElement = dom.getDocumentElement();
        NodeList schemeNodeList = rootElement.getElementsByTagName("Scheme");

        int entryNumber = 1; // for sorting by original order

        if (schemeNodeList != null && schemeNodeList.getLength() > 0) {
            for (int i = 0; i < schemeNodeList.getLength(); i++) {

                Element schemeElement = (Element) schemeNodeList.item(i);

                if (schemeElement != null) {
                    boolean validEntry = true;
                    boolean fieldsInitialized = false;

                    String id = null;
                    Double min = null;
                    Double max = null;
                    boolean logScaled = false;
                    String standardCpdFilename = null;
                    String universalCpdFilename = null;
                    String colorBarTitle = null;
                    String colorBarLabels = null;
                    String description = null;
                    String rootSchemeName = null;
                    String displayName = null;

                    File standardCpdFile = null;
                    File colorBlindCpdFile = null;
                    File cpdFile = null;

                    boolean enabled = true;

                    boolean overRide = false;

                    id = schemeElement.getAttribute("name");
                    description = getTextValue(schemeElement, "DESCRIPTION");
                    displayName = getTextValue(schemeElement, "DISPLAY_NAME");
                    colorBarLabels = getTextValue(schemeElement, "COLORBAR_LABELS");
                    colorBarTitle = getTextValue(schemeElement, "COLORBAR_TITLE");
                    String minStr = getTextValue(schemeElement, "MIN");
                    String maxStr = getTextValue(schemeElement, "MAX");
                    String logScaledStr = getTextValue(schemeElement, "LOG_SCALE");
                    standardCpdFilename = getTextValue(schemeElement, "CPD_FILENAME");
                    universalCpdFilename = getTextValue(schemeElement, "CPD_FILENAME_COLORBLIND");


//                    if (cpdFileNameStandard == null ||
//                            cpdFileNameStandard.length() == 0) {
//                        cpdFileNameStandard = ColorSchemeDefaults.CPD_DEFAULT;
//                    }


                    if (minStr != null && minStr.length() > 0) {
                        min = Double.valueOf(minStr);
                    } else {
                        min = ColorSchemeDefaults.DOUBLE_NULL;
                    }

                    if (maxStr != null && maxStr.length() > 0) {
                        max = Double.valueOf(maxStr);
                    } else {
                        max = ColorSchemeDefaults.DOUBLE_NULL;
                    }

                    logScaled = false;
                    if (logScaledStr != null && logScaledStr.length() > 0 && logScaledStr.toLowerCase().equals("true")) {
                        logScaled = true;
                    }


                    if (id != null && id.length() > 0) {
                        fieldsInitialized = true;
                    }

                    if (fieldsInitialized) {

                        if (!testMinMax(min, max, logScaled)) {
                            validEntry = false;
                        }

                        if (validEntry) {
                            if (standardCpdFilename != null) {
                                standardCpdFile = new File(colorPaletteAuxDir, standardCpdFilename);
                                if (standardCpdFile == null || !standardCpdFile.exists()) {
                                    validEntry = false;
                                }
                            } else {
                                validEntry = false;
                            }
                        }

                        if (validEntry) {
                            if (universalCpdFilename != null) {
                                colorBlindCpdFile = new File(colorPaletteAuxDir, universalCpdFilename);
                                if (colorBlindCpdFile == null || !colorBlindCpdFile.exists()) {
                                    validEntry = false;
                                }
                            } else {
                                validEntry = false;
                            }
                        }

                        if (validEntry) {
                            cpdFile = standardCpdFile;
                        }


//                        if (validEntry) {
                        ColorSchemeInfo colorSchemeInfo = null;

                        enabled = validEntry;

                        colorSchemeInfo = new ColorSchemeInfo(id, entryNumber, displayName, rootSchemeName, description, standardCpdFilename, min, max, logScaled, overRide, enabled, universalCpdFilename, colorBarTitle, colorBarLabels, colorPaletteAuxDir);

                        entryNumber++;

//                        try {
//                            // todo what does this do?
//                            if (1 == 2 && validEntry) {
//                                ColorPaletteDef.loadColorPaletteDef(cpdFile);
//                            }
//                            colorSchemeInfo = new ColorSchemeInfo(id, entryNumber, displayName, rootSchemeName, description, standardCpdFilename, min, max, logScaled, overRide, enabled, universalCpdFilename, colorBarTitle, colorBarLabels, colorPaletteAuxDir);
//
//                            entryNumber++;
//
//                        } catch (IOException e) {
//                        }


                        if (colorSchemeInfo != null) {
                            if (overRide) {
                                // look for previous name which user may be overriding and delete it in the colorSchemeInfo object
                                ColorSchemeInfo colorSchemeInfoToDelete = null;
                                for (ColorSchemeInfo storedColorSchemeInfo : colorSchemeInfos) {
                                    if (storedColorSchemeInfo.getName().equals(id)) {
                                        colorSchemeInfoToDelete = storedColorSchemeInfo;
                                        break;
                                    }
                                }
                                if (colorSchemeInfoToDelete != null) {
                                    colorSchemeInfos.remove(colorSchemeInfoToDelete);
                                }
                            }
                            colorSchemeInfos.add(colorSchemeInfo);
                        }
//                        }
                    }
                }
            }
        }
        return true;
    }

    private void initColorSchemeLut() {

        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        Document dom = null;

        try {
            DocumentBuilder db = dbf.newDocumentBuilder();
            dom = db.parse(new FileInputStream(colorSchemeLutFile));
        } catch (ParserConfigurationException pce) {
            pce.printStackTrace();
        } catch (SAXException se) {
            se.printStackTrace();
        } catch (IOException ioe) {
            ioe.printStackTrace();
        }

        Element rootElement = dom.getDocumentElement();
        NodeList keyNodeList = rootElement.getElementsByTagName("KEY");

        if (keyNodeList != null && keyNodeList.getLength() > 0) {

            for (int i = 0; i < keyNodeList.getLength(); i++) {

                Element schemeElement = (Element) keyNodeList.item(i);
                if (schemeElement != null) {
                    boolean fieldsInitialized = false;

                    String name = null;
                    Double minVal = null;
                    Double maxVal = null;
                    boolean logScaled = false;
                    String cpdFileNameStandard = null;
                    boolean overRide = false;
                    String description = null;
                    String rootSchemeName = null;
                    String cpdFileNameColorBlind = null;
                    String colorBarTitle = null;
                    String colorBarLabels = null;

                    String desiredScheme = null;

                    File standardCpdFile = null;
                    File cpdFile = null;


                    name = schemeElement.getAttribute("REGEX");

                    desiredScheme = getTextValue(schemeElement, "SCHEME_ID");

                    if (desiredScheme == null || desiredScheme.length() == 0) {
                        desiredScheme = name;
                    }

                    description = getTextValue(schemeElement, "DESCRIPTION");


                    if (name != null && name.length() > 0 && desiredScheme != null && desiredScheme.length() > 0) {

                        for (ColorSchemeInfo storedColorSchemeInfo : colorSchemeInfos) {
                            if (storedColorSchemeInfo.getName().toLowerCase().equals(desiredScheme.toLowerCase())) {
                                if (!fieldsInitialized ||
                                        (fieldsInitialized && overRide)) {

                                    cpdFileNameStandard = storedColorSchemeInfo.getCpdFilenameStandard();
                                    minVal = storedColorSchemeInfo.getMinValue();
                                    maxVal = storedColorSchemeInfo.getMaxValue();
                                    logScaled = storedColorSchemeInfo.isLogScaled();
                                    rootSchemeName = desiredScheme;
                                    cpdFileNameColorBlind = storedColorSchemeInfo.getCpdFilenameColorBlind();
                                    colorBarTitle = storedColorSchemeInfo.getColorBarTitle();
                                    colorBarLabels = storedColorSchemeInfo.getColorBarLabels();
                                    description = storedColorSchemeInfo.getDescription();

                                    fieldsInitialized = true;
                                }
                            }
                        }
                    }

                    if (fieldsInitialized) {

                        standardCpdFile = new File(colorPaletteAuxDir, cpdFileNameStandard);

                        cpdFile = standardCpdFile;

                        ColorSchemeInfo colorSchemeInfo = null;


                        try {
                            ColorPaletteDef.loadColorPaletteDef(cpdFile);
                            colorSchemeInfo = new ColorSchemeInfo(name, 0, null, rootSchemeName, description, cpdFileNameStandard, minVal, maxVal, logScaled, overRide, true, cpdFileNameColorBlind, colorBarTitle, colorBarLabels, colorPaletteAuxDir);

                        } catch (IOException e) {
                        }


                        if (colorSchemeInfo != null) {
                            colorSchemeLutInfos.add(colorSchemeInfo);
                        }
                    }
                }
            }
        }
    }


    private boolean testMinMax(double min, double max, boolean isLogScaled) {
        boolean checksOut = true;

        if (min != ColorSchemeDefaults.DOUBLE_NULL && max != ColorSchemeDefaults.DOUBLE_NULL) {
            if (min == max) {
                checksOut = false;
            }
        }


        if (min != ColorSchemeDefaults.DOUBLE_NULL && max != ColorSchemeDefaults.DOUBLE_NULL) {
            if (isLogScaled && min == 0) {
                checksOut = false;
            }
        }

        return checksOut;
    }

    public void setSelected(ColorSchemeInfo colorSchemeInfo) {
        // loop through and find rootScheme
        if (jComboBox != null) {
            if (colorSchemeInfo != null) {
                if (colorSchemeInfo.getRootName() != null) {
                    for (ColorSchemeInfo storedColorSchemeInfo : colorSchemeInfos) {
                        if (colorSchemeInfo.getRootName().equals(storedColorSchemeInfo.getName())) {
                            jComboBox.setSelectedItem(storedColorSchemeInfo);
                        }
                    }
                } else {
                    jComboBox.setSelectedItem(colorSchemeInfo);
                }
            } else {
                reset();
            }
        }

    }

    public void reset() {
        if (jComboBox != null) {
            jComboBox.setSelectedItem(jComboBoxFirstEntryColorSchemeInfo);
        }
    }

    public boolean isSchemeSet() {
        if (jComboBox != null && jComboBoxFirstEntryColorSchemeInfo != jComboBox.getSelectedItem()) {
            return true;
        }

        return false;
    }


    public ColorSchemeInfo setSchemeName(String schemeName) {

        if (schemeName != null) {
            for (ColorSchemeInfo colorSchemeInfo : colorSchemeLutInfos) {
                if (schemeName.trim().equals(colorSchemeInfo.getName().trim())) {
                    jComboBox.setSelectedItem(colorSchemeInfo);
                    return colorSchemeInfo;
                }
            }
        }

        return null;
    }


    public static String getTextValue(Element ele, String tagName) {
        String textVal = null;
        NodeList nl = ele.getElementsByTagName(tagName);
        if (nl != null && nl.getLength() > 0) {
            Element el = (Element) nl.item(0);

            if (el.hasChildNodes()) {
                textVal = el.getFirstChild().getNodeValue();
            }

        }

        return textVal;
    }

    public ArrayList<String> readFileIntoArrayList(File file) {
        String lineData;
        ArrayList<String> fileContents = new ArrayList<String>();
        BufferedReader moFile = null;
        try {
            moFile = new BufferedReader(new FileReader(file));
            while ((lineData = moFile.readLine()) != null) {

                fileContents.add(lineData);
            }
        } catch (IOException e) {
            ;
        } finally {
            try {
                moFile.close();
            } catch (Exception e) {
                //Ignore
            }
        }
        return fileContents;
    }

    public JComboBox getjComboBox() {
        return jComboBox;
    }

    public ArrayList<ColorSchemeInfo> getColorSchemeLutInfos() {
        return colorSchemeLutInfos;
    }

    private boolean isVerbose() {
        return verbose;
    }

    private void setVerbose(boolean verbose) {
        this.verbose = verbose;

        for (ColorSchemeInfo colorSchemeInfo : colorSchemeInfos) {
            colorSchemeInfo.setUseDisplayName(verbose);
        }
    }


    class MyComboBoxRenderer extends BasicComboBoxRenderer {

        private String[] tooltips;
        private Boolean[] enabledList;

        public Component getListCellRendererComponent(JList list, Object value,
                                                      int index, boolean isSelected, boolean cellHasFocus) {

            if (index >= 0 && index < enabledList.length) {
                setEnabled(enabledList[index]);
                setFocusable(enabledList[index]);
            }


            if (isSelected) {
                setBackground(Color.blue);

                if (index >= 0 && index < tooltips.length) {
                    list.setToolTipText(tooltips[index]);
                }

                if (index >= 0 && index < enabledList.length) {

                    if (enabledList[index]) {
                        setForeground(Color.white);
                    } else {
                        setForeground(Color.gray);
                    }
                }

            } else {
                setBackground(Color.white);

                if (index >= 0 && index < enabledList.length) {
                    if (enabledList[index] == true) {
                        setForeground(Color.black);
                    } else {
                        setForeground(Color.gray);
                    }

                }
            }


            if (index == 0) {
                setForeground(Color.black);
            }

            setFont(list.getFont());
            setText((value == null) ? "" : value.toString());


            return this;
        }

        public void setTooltipList(String[] tooltipList) {
            this.tooltips = tooltipList;
        }


        public void setEnabledList(Boolean[] enabledList) {
            this.enabledList = enabledList;
        }
    }


    public String getjComboBoxFirstEntryName() {
        return jComboBoxFirstEntryName;
    }

    private void setjComboBoxFirstEntryName(String jComboBoxFirstEntryName) {
        this.jComboBoxFirstEntryName = jComboBoxFirstEntryName;
    }

}
