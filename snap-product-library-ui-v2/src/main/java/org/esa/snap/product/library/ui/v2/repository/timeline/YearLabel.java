package org.esa.snap.product.library.ui.v2.repository.timeline;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class YearLabel extends JLabel {

    private final int year;
    private final List<TimelineBarComponent> timelineBars;

    public YearLabel(int year) {
        super(Integer.toString(year), JLabel.CENTER);

        this.year = year;

        this.timelineBars = new ArrayList<>();
    }

    public int getYear() {
        return year;
    }

    public TimelineBarComponent findBarComponentById(int barId) {
        for (int k = 0; k<this.timelineBars.size(); k++) {
            TimelineBarComponent barComponent = this.timelineBars.get(k);
            if (barComponent.getYearLabel().getYear() == this.year && barComponent.getId() == barId) {
                return barComponent;
            }
        }
        return null;
    }

    public TimelineBarComponent addBarComponent(int barId) {
        TimelineBarComponent barComponent = new TimelineBarComponent(this, barId);
        barComponent.setBackground(RepositoryProductsTimelinePanel.BAR_CHART_COLOR);
        barComponent.setOpaque(true);
        this.timelineBars.add(barComponent);
        return barComponent;
    }

    public int getTimelineBarCount() {
        return this.timelineBars.size();
    }

    public TimelineBarComponent getTimelineBarAt(int index) {
        return this.timelineBars.get(index);
    }

    public int computeMaximumProductCount() {
        int maximumProductCount = this.timelineBars.get(0).getProductCount();
        for (int i = 1; i < this.timelineBars.size(); i++) {
            int productCount = this.timelineBars.get(i).getProductCount();
            if (maximumProductCount < productCount) {
                maximumProductCount = productCount;
            }
        }
        return maximumProductCount;
    }

    public void sortBars(Comparator<TimelineBarComponent> barsComparator) {
        Collections.sort(this.timelineBars, barsComparator);
    }
}
