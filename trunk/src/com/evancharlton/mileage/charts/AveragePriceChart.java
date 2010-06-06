package com.evancharlton.mileage.charts;

import android.database.Cursor;

import com.artfulbits.aiCharts.Base.ChartPoint;
import com.artfulbits.aiCharts.Base.ChartPointCollection;
import com.evancharlton.mileage.R;
import com.evancharlton.mileage.dao.Vehicle;

public class AveragePriceChart extends PriceChart {
	@Override
	protected String getAxisTitle() {
		return getString(R.string.stat_avg_price);
	}

	@Override
	protected void processCursor(LineChartGenerator generator, ChartPointCollection points, Cursor cursor, Vehicle vehicle) {
		int num = 0;
		while (cursor.isAfterLast() == false) {
			if (generator.isCancelled()) {
				break;
			}
			points.add(new ChartPoint(num, cursor.getDouble(0)));
			generator.update(num++);
			cursor.moveToNext();
		}
	}
}