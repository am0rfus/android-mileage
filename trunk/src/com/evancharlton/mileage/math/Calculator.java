package com.evancharlton.mileage.math;

import java.util.Currency;
import java.util.Locale;

import android.content.Context;

import com.evancharlton.mileage.R;
import com.evancharlton.mileage.dao.Fillup;
import com.evancharlton.mileage.dao.FillupSeries;
import com.evancharlton.mileage.dao.Vehicle;

public class Calculator {
	// dates
	public static final long DAY_MS = 1000L * 60L * 60L * 24L;
	public static final long MONTH_MS = DAY_MS * 30L;
	public static final long YEAR_MS = DAY_MS * 365L;

	// distance
	public static final int KM = 1;
	public static final int MI = 2;

	// volume
	public static final int GALLONS = 3;
	public static final int LITRES = 4;
	public static final int IMPERIAL_GALLONS = 5;

	// economy
	public static final int MI_PER_GALLON = 6;
	public static final int KM_PER_GALLON = 7;
	public static final int MI_PER_IMP_GALLON = 8;
	public static final int KM_PER_IMP_GALLON = 9;
	public static final int MI_PER_LITRE = 10;
	public static final int KM_PER_LITRE = 11;
	public static final int GALLONS_PER_100KM = 12;
	public static final int LITRES_PER_100KM = 13;
	public static final int IMP_GAL_PER_100KM = 14;

	// cache
	private static String CURRENCY_SYMBOL = null;

	/**
	 * Returns a positive integer if first is a *better* economy than second, a
	 * negative integer if second is *better* than first, and 0 if the two are
	 * equal.
	 * 
	 * @param first value of the first economy
	 * @param firstUnit units on the first economy
	 * @param second value of the second economy
	 * @param secondUnit units on the second economy
	 * @return positive if first is better than second, negative if second is
	 *         better, and 0 if equal
	 */
	public static int compareEconomies(double first, int firstUnit, double second, int secondUnit) {
		if (firstUnit == secondUnit) {
			switch (firstUnit) {
				case GALLONS_PER_100KM:
				case LITRES_PER_100KM:
				case IMP_GAL_PER_100KM:
					if (first < second) {
						return 1;
					} else if (first > second) {
						return -1;
					}
					return 0;
				case MI_PER_GALLON:
				case KM_PER_GALLON:
				case MI_PER_IMP_GALLON:
				case KM_PER_IMP_GALLON:
				case MI_PER_LITRE:
				case KM_PER_LITRE:
				default:
					if (first > second) {
						return 1;
					} else if (first < second) {
						return -1;
					}
					return 0;
			}
		} else {
			double converted = convert(second, secondUnit, firstUnit);
			return compareEconomies(first, firstUnit, converted, firstUnit);
		}
	}

	public static double averageEconomy(Vehicle vehicle, Fillup fillup) {
		if (!fillup.hasPrevious()) {
			throw new IllegalArgumentException("You can't calculate economy on one fillup");
		}
		return averageEconomy(vehicle, new FillupSeries(fillup.getPrevious(), fillup));
	}

	/**
	 * @param vehicle
	 * @param first
	 * @param second
	 * @return true if first is BETTER than second
	 */
	public static boolean isBetterEconomy(Vehicle vehicle, double first, double second) {
		switch (vehicle.getEconomyUnits()) {
			case GALLONS_PER_100KM:
			case LITRES_PER_100KM:
			case IMP_GAL_PER_100KM:
				return first <= second;
		}
		return first >= second;
	}

	public static double averageEconomy(Vehicle vehicle, FillupSeries series) {
		// ALL CALCULATIONS ARE DONE IN MPG AND CONVERTED LATER
		double miles = convert(series.getTotalDistance(), vehicle.getDistanceUnits(), MI);
		double gallons = convert(series.getEconomyVolume(), vehicle.getVolumeUnits(), GALLONS);

		switch (vehicle.getEconomyUnits()) {
			case KM_PER_GALLON:
				return convert(miles, KM) / gallons;
			case MI_PER_IMP_GALLON:
				return miles / convert(gallons, IMPERIAL_GALLONS);
			case KM_PER_IMP_GALLON:
				return convert(miles, KM) / convert(gallons, IMPERIAL_GALLONS);
			case MI_PER_LITRE:
				return miles / convert(gallons, LITRES);
			case KM_PER_LITRE:
				return convert(miles, KM) / convert(gallons, LITRES);
			case GALLONS_PER_100KM:
				return (100D * gallons) / convert(miles, KM);
			case LITRES_PER_100KM:
				return (100D * convert(gallons, LITRES)) / convert(miles, KM);
			case IMP_GAL_PER_100KM:
				return (100D * convert(gallons, IMPERIAL_GALLONS)) / convert(miles, KM);
			case MI_PER_GALLON:
			default:
				return miles / gallons;
		}
	}

	public static double averageDistanceBetweenFillups(FillupSeries series) {
		return series.getTotalDistance() / (series.size() - 1);
	}

	public static double averageFillupVolume(FillupSeries series) {
		return series.getTotalVolume() / series.size();
	}

	public static double averageFillupCost(FillupSeries series) {
		return series.getTotalCost() / series.size();
	}

	public static double averageCostPerDistance(FillupSeries series) {
		return series.getTotalCost() / series.getTotalDistance();
	}

	public static double averageFuelPerDay(FillupSeries series) {
		long timeRange = series.getTimeRange();
		double numDays = Math.ceil((double) timeRange / (double) DAY_MS);
		return series.getTotalVolume() / numDays;
	}

	public static double averageCostPerDay(FillupSeries series) {
		long timeRange = series.getTimeRange();
		double numDays = Math.ceil((double) timeRange / (double) DAY_MS);
		return series.getTotalCost() / numDays;
	}

	public static double averagePrice(FillupSeries series) {
		double total = 0D;
		final int SIZE = series.size();
		for (int i = 0; i < SIZE; i++) {
			Fillup fillup = series.get(i);
			total += fillup.getUnitPrice();
		}
		return total / SIZE;
	}

	// yes, this method makes it possible to convert from miles to litres.
	// if you do this, I'll hunt you down and beat you with a rubber hose.
	public static double convert(double value, int from, int to) {
		// going from whatever to miles or gallons (depending on context)
		switch (from) {
			case KM:
				value *= 0.621371192;
				break;
			case LITRES:
				value *= 0.264172052;
				break;
			case IMPERIAL_GALLONS:
				value *= 1.20095042;
				break;
			case KM_PER_GALLON:
				value *= 0.621371192;
				break;
			case MI_PER_IMP_GALLON:
				value *= 0.83267384;
				break;
			case KM_PER_IMP_GALLON:
				value *= 0.517399537;
				break;
			case MI_PER_LITRE:
				value *= 3.78541178;
				break;
			case KM_PER_LITRE:
				value *= 2.35214583;
				break;
			case GALLONS_PER_100KM:
				value *= 62.1371192;
				break;
			case LITRES_PER_100KM:
				value *= 235.214583;
				break;
			case IMP_GAL_PER_100KM:
				value *= 51.7399537;
				break;
			case MI:
			case GALLONS:
			default:
				break;
		}
		// at this point, "value" is either miles or gallons
		return convert(value, to);
	}

	// convert from (miles|gallons) to the other unit
	private static double convert(double value, int to) {
		// value is now converted to miles or gallons
		switch (to) {
			case MI:
				return value;
			case KM:
				return value /= 0.621371192;
			case GALLONS:
				return value;
			case LITRES:
				return value /= 0.264172052;
			case IMPERIAL_GALLONS:
				return value /= 1.20095042;
			case MI_PER_GALLON:
				return value;
			case MI_PER_LITRE:
				return value *= 0.264172052;
			case MI_PER_IMP_GALLON:
				return value *= 1.20095042;
			case KM_PER_GALLON:
				return value *= 1.609344;
			case KM_PER_LITRE:
				return value *= 0.425143707;
			case KM_PER_IMP_GALLON:
				return value *= 1.93274236;
			case GALLONS_PER_100KM:
				return value *= 62.1371192;
			case LITRES_PER_100KM:
				return value *= 235.214583;
			case IMP_GAL_PER_100KM:
				return value *= 51.7399537;
		}
		return value;
	}

	public static String getVolumeUnits(Context context, Vehicle vehicle) {
		switch (vehicle.getVolumeUnits()) {
			case LITRES:
				return context.getString(R.string.units_litres);
			case IMPERIAL_GALLONS:
			case GALLONS:
			default:
				return context.getString(R.string.units_gallons);
		}
	}

	public static String getVolumeUnitsAbbr(Context context, Vehicle vehicle) {
		switch (vehicle.getVolumeUnits()) {
			case LITRES:
				return context.getString(R.string.units_litres_abbr);
			case IMPERIAL_GALLONS:
			case GALLONS:
			default:
				return context.getString(R.string.units_gallons_abbr);
		}
	}

	public static String getDistanceUnits(Context context, Vehicle vehicle) {
		switch (vehicle.getDistanceUnits()) {
			case KM:
				return context.getString(R.string.units_kilometers);
			case MI:
			default:
				return context.getString(R.string.units_miles);
		}
	}

	public static String getDistanceUnitsAbbr(Context context, Vehicle vehicle) {
		switch (vehicle.getDistanceUnits()) {
			case KM:
				return context.getString(R.string.units_kilometers_abbr);
			case MI:
			default:
				return context.getString(R.string.units_miles_abbr);
		}
	}

	public static String getEconomyUnitsAbbr(Context context, Vehicle vehicle) {
		switch (vehicle.getEconomyUnits()) {
			case KM_PER_GALLON:
			case KM_PER_IMP_GALLON:
				return context.getString(R.string.units_kmpg);
			case MI_PER_LITRE:
				return context.getString(R.string.units_mpl);
			case KM_PER_LITRE:
				return context.getString(R.string.units_kmpl);
			case LITRES_PER_100KM:
				return context.getString(R.string.units_lpckm);
			case GALLONS_PER_100KM:
			case IMP_GAL_PER_100KM:
				return context.getString(R.string.units_gpckm);
			case MI_PER_GALLON:
			case MI_PER_IMP_GALLON:
			default:
				return context.getString(R.string.units_mpg);
		}
	}

	public static String getCurrencySymbol() {
		if (CURRENCY_SYMBOL == null) {
			CURRENCY_SYMBOL = Currency.getInstance(Locale.getDefault()).getSymbol();
		}
		return CURRENCY_SYMBOL;
	}
};