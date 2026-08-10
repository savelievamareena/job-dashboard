package com.savelieva.jobdashboard.model;

import java.util.List;

/**
 * Everything the statistics page needs, in one response: three trends and the days a search
 * actually ran.
 *
 * <p>scanDays is not decoration. A view can only return the days it has rows for, so it cannot
 * say whether a day is missing because nothing was posted or because nobody looked. Without this
 * list the page would draw a zero for a day with no search run, which reads as the market
 * dropping to nothing and recovering.
 *
 * @see <a href="file:../../../../../../../db/schema.sql">db/schema.sql</a>
 */
public record Statistics(
        List<TrendPoint> language,
        List<TrendPoint> layer,
        List<TrendPoint> ai,
        List<String> scanDays) {
}
