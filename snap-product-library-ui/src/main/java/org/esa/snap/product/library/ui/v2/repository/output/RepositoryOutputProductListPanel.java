package org.esa.snap.product.library.ui.v2.repository.output;

import org.esa.snap.product.library.ui.v2.ComponentDimension;
import org.esa.snap.product.library.ui.v2.repository.RepositorySelectionPanel;
import org.esa.snap.product.library.ui.v2.thread.ProgressBarHelperImpl;
import org.esa.snap.remote.products.repository.RepositoryProduct;
import org.esa.snap.ui.loading.SwingUtils;

import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.*;

/**
 * Created by jcoravu on 21/8/2019.
 */
public class RepositoryOutputProductListPanel extends JPanel implements OutputProductResultsCallback {

    private static final String PAGE_PRODUCTS_CHANGED = "pageProductsChanged";

    private static final byte PRODUCT_COUNT_PER_PAGE = 20;

    private final JLabel titleLabel;
    private final JLabel sortByLabel;
    private final ProgressBarHelperImpl progressBarHelper;
    private final OutputProductListPanel productListPanel;
    private final OutputProductListPaginationPanel productListPaginationPanel;
    private final Map<String, Comparator<RepositoryProduct>> availableComparators;

    private Comparator<RepositoryProduct> currentComparator;

    private OutputProductResults outputProductResults;
    private List<RepositoryProduct> availableProducts;
    private int currentPageNumber;

    public RepositoryOutputProductListPanel(RepositorySelectionPanel repositorySelectionPanel, ComponentDimension componentDimension,
                                            ActionListener stopButtonListener, int progressBarWidth) {

        super(new BorderLayout(0, componentDimension.getGapBetweenRows()/2));

        this.titleLabel = new JLabel(getTitle());
        Dimension size = this.titleLabel.getPreferredSize();
        size.height += 2; // add more pixels
        this.titleLabel.setPreferredSize(size);

        this.sortByLabel = new JLabel();
        this.sortByLabel.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent mouseEvent) {
                if (SwingUtilities.isLeftMouseButton(mouseEvent)) {
                    showProductsPopupMenu(mouseEvent.getX(), mouseEvent.getY());
                }
            }
        });

        String currentComparatorName = "Product Name";
        this.availableComparators = new LinkedHashMap<>();
        this.availableComparators.put(currentComparatorName, buildProductNameComparator());
        this.availableComparators.put("Mission", buildMissionComparator());
        this.availableComparators.put("Acquisition Date", buildAcquisitionDateComparator());
        this.availableComparators.put("File Size", buildFileSizeComparator());

        resetOutputProducts();

        this.productListPanel = new OutputProductListPanel(repositorySelectionPanel, componentDimension, this);
        this.productListPanel.addDataChangedListener(new PropertyChangeListener() {
            @Override
            public void propertyChange(PropertyChangeEvent evt) {
                updateProductListCountTitle();
            }
        });

        ActionListener firstPageButtonListener = new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent actionEvent) {
                displayFirstPageProducts();
            }
        };
        ActionListener previousPageButtonListener = new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent actionEvent) {
                displayPreviousPageProducts();
            }
        };
        ActionListener nextPageButtonListener = new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent actionEvent) {
                displayNextPageProducts();
            }
        };
        ActionListener lastPageButtonListener = new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent actionEvent) {
                displayLastPageProducts();
            }
        };
        this.productListPaginationPanel = new OutputProductListPaginationPanel(componentDimension, firstPageButtonListener, previousPageButtonListener,
                                                                               nextPageButtonListener, lastPageButtonListener);

        this.progressBarHelper = new ProgressBarHelperImpl(progressBarWidth, size.height) {
            @Override
            protected void setParametersEnabledWhileDownloading(boolean enabled) {
                // do nothing
            }
        };
        this.progressBarHelper.getStopButton().addActionListener(stopButtonListener);

        setCurrentComparator(currentComparatorName);

        addComponents(componentDimension);
    }

    @Override
    public Comparator<RepositoryProduct> getProductsComparator() {
        return this.currentComparator;
    }

    @Override
    public OutputProductResults getOutputProductResults() {
        return this.outputProductResults;
    }

    public OutputProductListPaginationPanel getProductListPaginationPanel() {
        return this.productListPaginationPanel;
    }

    public ProgressBarHelperImpl getProgressBarHelper() {
        return progressBarHelper;
    }

    public OutputProductListPanel getProductListPanel() {
        return productListPanel;
    }

    public void addPageProductsChangedListener(PropertyChangeListener changeListener) {
        addPropertyChangeListener(PAGE_PRODUCTS_CHANGED, changeListener);
    }

    public void setProducts(List<RepositoryProduct> products) {
        resetOutputProducts();
        if (products.size() > 0) {
            this.availableProducts.addAll(products);
            displayPageProducts(0, 1); // display the first page
        } else {
            this.productListPanel.getProductListModel().clear();
            refreshPaginationButtons();
        }
    }

    public void clearOutputList() {
        resetOutputProducts();
        this.productListPanel.getProductListModel().clear();
        refreshPaginationButtons();
        resetProductListCountTitle();
    }

    public void setProductQuickLookImage(RepositoryProduct repositoryProduct, BufferedImage quickLookImage) {
        for (int i=0; i<this.availableProducts.size(); i++) {
            RepositoryProduct existingProduct = this.availableProducts.get(i);
            if (existingProduct == repositoryProduct) {
                existingProduct.setQuickLookImage(quickLookImage);
                this.productListPanel.getProductListModel().updateProductQuickLookImage(repositoryProduct);
                return;
            }
        }
    }

    public void addProducts(List<RepositoryProduct> products) {
        if (products.size() > 0) {
            if (this.availableProducts.size() > 0) {
                if (this.currentPageNumber <= 0) {
                    throw new IllegalStateException("The current page number " + this.currentPageNumber + " must be > 0.");
                }
            } else if (this.currentPageNumber != 0) {
                throw new IllegalStateException("The current page number " + this.currentPageNumber + " must be 0.");
            }

            this.availableProducts.addAll(products);
            if (this.currentPageNumber == 0) {
                // the first page
                int productCount = this.productListPanel.getProductListModel().getProductCount();
                if (productCount > 0) {
                    throw new IllegalStateException("The product count " + productCount + " of the first page must be 0.");
                }
                displayPageProducts(0, 1); // display the first page
            } else {
                refreshPaginationButtons();
            }
        }
    }

    private void displayNextPageProducts() {
        int totalPageCount = computeTotalPageCount();
        if (this.currentPageNumber < totalPageCount) {
            displayPageProducts(this.currentPageNumber, this.currentPageNumber + 1);
        } else {
            throw new IllegalStateException("The current page number " + this.currentPageNumber+" must be < than the total page count " + totalPageCount + ".");
        }
    }

    private void displayLastPageProducts() {
        int totalPageCount = computeTotalPageCount();
        if (this.currentPageNumber < totalPageCount) {
            displayPageProducts(totalPageCount - 1, totalPageCount);
        } else {
            throw new IllegalStateException("The current page number " + this.currentPageNumber+" must be < than the total page count " + totalPageCount + ".");
        }
    }

    private void displayFirstPageProducts() {
        if (this.currentPageNumber > 1) {
            displayPageProducts(0, 1);
        } else {
            int totalPageCount = computeTotalPageCount();
            throw new IllegalStateException("The current page number " + this.currentPageNumber+" must be < than the total page count " + totalPageCount + ".");
        }
    }

    private void displayPreviousPageProducts() {
        if (this.currentPageNumber > 1) {
            displayPageProducts(this.currentPageNumber - 2, this.currentPageNumber - 1);
        } else {
            int totalPageCount = computeTotalPageCount();
            throw new IllegalStateException("The current page number " + this.currentPageNumber+" must be < than the total page count " + totalPageCount + ".");
        }
    }

    private void displayPageProducts(int pageIndex, int newCurrentPageNumber) {
        int startIndex = pageIndex * PRODUCT_COUNT_PER_PAGE;
        int endIndex = startIndex + PRODUCT_COUNT_PER_PAGE - 1;
        if (endIndex >= this.availableProducts.size()) {
            endIndex = this.availableProducts.size() - 1;
        }
        List<RepositoryProduct> pageProducts = new ArrayList<>((endIndex - startIndex));
        for (int i = startIndex; i <= endIndex; i++) {
            pageProducts.add(this.availableProducts.get(i));
        }
        this.currentPageNumber = newCurrentPageNumber;
        this.productListPanel.setProducts(pageProducts);
        refreshPaginationButtons();
        firePropertyChange(PAGE_PRODUCTS_CHANGED, null, null);
    }

    private void refreshPaginationButtons() {
        int totalPageCount = computeTotalPageCount();
        boolean previousPageEnabled = false;
        boolean nextPageEnabled = false;
        String text = "";
        if (totalPageCount > 0) {
            if (this.currentPageNumber > 0) {
                text = Integer.toString(this.currentPageNumber) + " / " + Integer.toString(totalPageCount);
                if (this.currentPageNumber > 1) {
                    previousPageEnabled = true;
                }
                if (this.currentPageNumber < totalPageCount) {
                    nextPageEnabled = true;
                }
            } else {
                throw new IllegalStateException("The current page number is 0.");
            }
        }
        this.productListPaginationPanel.refreshPaginationButtons(previousPageEnabled, nextPageEnabled, text);
    }

    private int computeTotalPageCount() {
        int count = 0;
        if (this.availableProducts.size() > 0) {
            count = this.availableProducts.size() / PRODUCT_COUNT_PER_PAGE;
            if (this.availableProducts.size() % PRODUCT_COUNT_PER_PAGE > 0) {
                count++;
            }
        }
        return count;
    }

    private void resetProductListCountTitle() {
        this.titleLabel.setText(getTitle());
    }

    public void updateProductListCountTitle() {
        int pageProductCount = this.productListPanel.getProductListModel().getProductCount();
        int totalProductCount = this.availableProducts.size();
        String text = getTitle() + ": " + Integer.toString(pageProductCount);
        if (totalProductCount > 0) {
            text += " out of " + Integer.toString(totalProductCount);
        }
        this.titleLabel.setText(text);
    }

    private String getTitle() {
        return "Products";
    }

    private void showProductsPopupMenu(int mouseX, int mouseY) {
        JPopupMenu popup = new JPopupMenu();
        for (String displayName : this.availableComparators.keySet()) {
            JMenuItem menuItem = new JMenuItem(displayName);
            menuItem.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent actionEvent) {
                    JMenuItem item = (JMenuItem)actionEvent.getSource();
                    setCurrentComparator(item.getText());
                }
            });
            popup.add(menuItem);
        }
        popup.show(this.sortByLabel, mouseX, mouseY);
    }

    private void resetOutputProducts() {
        this.outputProductResults = new OutputProductResults();
        this.availableProducts = new ArrayList<>();
        this.currentPageNumber = 0;
    }

    private void setCurrentComparator(String displayName) {
        this.currentComparator = this.availableComparators.get(displayName);
        this.sortByLabel.setText("Sort By: " + displayName);
        this.productListPanel.getProductListModel().sortProducts();
    }

    private void addComponents(ComponentDimension componentDimension) {
        int bottomMargin = componentDimension.getGapBetweenRows() / 2;
        Insets progressBarMargins = new Insets(0, 0, bottomMargin, 0);
        Insets stopButtonMargins = new Insets(0, componentDimension.getGapBetweenRows(), bottomMargin, 0);

        JPanel northPanel = new JPanel(new GridBagLayout());
        GridBagConstraints c = SwingUtils.buildConstraints(0, 0, GridBagConstraints.BOTH, GridBagConstraints.SOUTH, 1, 1, bottomMargin, 0);
        northPanel.add(this.titleLabel, c);
        c = SwingUtils.buildConstraints(1, 0, GridBagConstraints.NONE, GridBagConstraints.WEST, 1, 1, progressBarMargins);
        northPanel.add(this.progressBarHelper.getProgressBar(), c);
        c = SwingUtils.buildConstraints(2, 0, GridBagConstraints.NONE, GridBagConstraints.WEST, 1, 1, stopButtonMargins);
        northPanel.add(this.progressBarHelper.getStopButton(), c);
        c = SwingUtils.buildConstraints(3, 0, GridBagConstraints.NONE, GridBagConstraints.SOUTH, 1, 1, bottomMargin, componentDimension.getGapBetweenColumns());
        northPanel.add(this.sortByLabel, c);

        JScrollPane scrollPane = new JScrollPane(this.productListPanel);
        scrollPane.getViewport().setOpaque(true);
        scrollPane.getViewport().setBackground(this.productListPanel.getBackground());

        add(northPanel, BorderLayout.NORTH);
        add(scrollPane, BorderLayout.CENTER);
    }

    private static Comparator<RepositoryProduct> buildProductNameComparator() {
        return new Comparator<RepositoryProduct>() {
            @Override
            public int compare(RepositoryProduct o1, RepositoryProduct o2) {
                return o1.getName().compareToIgnoreCase(o2.getName());
            }
        };
    }

    private static Comparator<RepositoryProduct> buildAcquisitionDateComparator() {
        return new Comparator<RepositoryProduct>() {
            @Override
            public int compare(RepositoryProduct o1, RepositoryProduct o2) {
                Date acquisitionDate1 = o1.getAcquisitionDate();
                Date acquisitionDate2 = o2.getAcquisitionDate();
                if (acquisitionDate1 == null && acquisitionDate2 == null) {
                    return 0; // both acquisition dates are null
                }
                if (acquisitionDate1 == null && acquisitionDate2 != null) {
                    return -1; // the first acquisition date is null
                }
                if (acquisitionDate1 != null && acquisitionDate2 == null) {
                    return 1; // the second acquisition date is null
                }
                return acquisitionDate1.compareTo(acquisitionDate2);
            }
        };
    }

    private static Comparator<RepositoryProduct> buildMissionComparator() {
        return new Comparator<RepositoryProduct>() {
            @Override
            public int compare(RepositoryProduct o1, RepositoryProduct o2) {
                if (o1.getMission() == null && o2.getMission() == null) {
                    return 0; // both missions are null
                }
                if (o1.getMission() == null && o2.getMission() != null) {
                    return -1; // the fist mission is null
                }
                if (o1.getMission() != null && o2.getMission() == null) {
                    return 1; // the second mission is null
                }
                return o1.getMission().compareToIgnoreCase(o2.getMission());
            }
        };
    }

    private static Comparator<RepositoryProduct> buildFileSizeComparator() {
        return new Comparator<RepositoryProduct>() {
            @Override
            public int compare(RepositoryProduct o1, RepositoryProduct o2) {
                long fileSize1 = o1.getApproximateSize();
                long fileSize2 = o2.getApproximateSize();
                if (fileSize1 == fileSize2) {
                    return 0;
                }
                if (fileSize1 < fileSize2) {
                    return -1;
                }
                return 1;
            }
        };
    }
}