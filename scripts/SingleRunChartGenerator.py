import matplotlib.pyplot as plt
import numpy as np
import pandas as pd
import argparse
from TrendlineGenerator import draw_trendline

def plot_runtime(df, ax, ax_color, window_size, line_style, trendline=False, polynomial=False):
    """
    Plot the runtime against the matching size on the given axis (left or right).

    :param df: data frame containing the data to plot
    :param ax: ax object to plot on
    :param ax_color: the color of the ax object
    :param window_size: window size for calculating the rolling average
    :param line_style: the style of the line based on if its the left or right axis
    :param trendline: overlay a best-fit trendline if True
    """

    if (window_size == -1):
        df["Runtime"] = df["Iteration Time"].cumsum()
    else:
        df["Runtime"] = df["Iteration Time"].rolling(window_size).mean()

    ax.plot(df["Matching Size"], df["Runtime"], color=ax_color, label="runtime", linestyle=line_style)
    ax.tick_params(axis='y', colors=ax_color)

    if trendline:
        clean = df[["Matching Size", "Runtime"]].dropna()
        if len(clean) > 200:
            clean = clean.iloc[np.linspace(0, len(clean) - 1, 200, dtype=int)]
        if len(clean) >= 4:
            fit = draw_trendline(clean["Matching Size"].values, clean["Runtime"].values, polynomial)
            if fit["predictor"]:
                x_s = np.linspace(clean["Matching Size"].min(), clean["Matching Size"].max(), 300)
                ax.plot(x_s, fit["predictor"](x_s), color=ax_color, linestyle=":",
                        alpha=0.7, label=f"{fit['name']} (R²={fit['r2']:.3f})")

def plot_operation(df, operation, ax, ax_color, window_size, line_style, trendline=False, polynomial=False):
    """
    Plot the specific operation against the matching size on the given axis (left or right).

    :param df: data frame containing the data to plot
    :param operation: the specific operation
    :param ax: ax object to plot on
    :param ax_color: the color of the ax object
    :param window_size: window size for calculating the rolling average
    :param line_style: the style of the line based on if its the left or right axis
    :param trendline: overlay a best-fit trendline if True
    :param polynomial: include polynomial (n^k) as a candidate fit if True
    """

    if (window_size == -1):
        df[f"{operation} Count"] = df[operation].cumsum()
    else:
        df[f"{operation} Count"] = df[operation].rolling(window_size).mean()

    ax.plot(df["Matching Size"], df[f"{operation} Count"], color=ax_color, label=operation, linestyle=line_style)
    ax.tick_params(axis='y', colors=ax_color)

    if trendline:
        col = f"{operation} Count"
        clean = df[["Matching Size", col]].dropna()
        if len(clean) > 200:
            clean = clean.iloc[np.linspace(0, len(clean) - 1, 200, dtype=int)]
        if len(clean) >= 4:
            fit = draw_trendline(clean["Matching Size"].values, clean[col].values, polynomial)
            if fit["predictor"]:
                x_s = np.linspace(clean["Matching Size"].min(), clean["Matching Size"].max(), 300)
                ax.plot(x_s, fit["predictor"](x_s), color=ax_color, linestyle=":",
                        alpha=0.7, label=f"{fit['name']} (R²={fit['r2']:.3f})")

def plot_pathlength(df, ax, ax_color, window_size, line_style, trendline=False, polynomial=False):
    """
    Plot the path length against the matching size on the given axis (left or right).

    :param df: data frame containing the data to plot
    :param ax: ax object to plot on
    :param ax_color: the color of the ax object
    :param window_size: window size for calculating the rolling average
    :param line_style: the style of the line based on if its the left or right axis
    :param trendline: overlay a best-fit trendline if True
    :param polynomial: include polynomial (n^k) as a candidate fit if True
    """

    if (window_size == -1):
        df["Path Length"] = df["Path Length"].cumsum()
    else:
        df["Path Length"] = (df["Path Length"].rolling(window_size).mean())

    ax.plot(df["Matching Size"], df["Path Length"], color=ax_color, label="pathLength", linestyle=line_style)
    ax.tick_params(axis='y', colors=ax_color)

    if trendline:
        clean = df[["Matching Size", "Path Length"]].dropna()
        if len(clean) > 200:
            clean = clean.iloc[np.linspace(0, len(clean) - 1, 200, dtype=int)]
        if len(clean) >= 4:
            fit = draw_trendline(clean["Matching Size"].values, clean["Path Length"].values, polynomial)
            if fit["predictor"]:
                x_s = np.linspace(clean["Matching Size"].min(), clean["Matching Size"].max(), 300)
                ax.plot(x_s, fit["predictor"](x_s), color=ax_color, linestyle=":",
                        alpha=0.7, label=f"{fit['name']} (R²={fit['r2']:.3f})")

def single_run_chart(csv_file_path, left_y_value, right_y_value, window, left_unit, right_unit, save_to, trendline=False, polynomial=False, title=None):
    """
    Based on the user input, generate a single-run chart from the data in the specified CSV file

    :param csv_file_path: csv file path containing the data to plot
    :param left_y_value: the values to plot on the left axis
    :param right_y_value: the values to plot on the right axis
    :param window: the window size
    :param left_unit: the unit for the left axis
    :param right_unit: the unit for the right axis
    :param save_to: file name to save the generated plot (if None, the plot will be displayed instead)
    :param trendline: overlay a best-fit trendline on each series if True
    :param polynomial: include polynomial (n^k) as a candidate fit if True
    """
    df = pd.read_csv(csv_file_path)

    fig, ax1 = plt.subplots()
    if right_y_value:
        ax2 = ax1.twinx()
    ax1.set_xlabel("Matching Size")

    COLOR_MAP = {
        "runtime": "blue",
        "pathLength": "green",
        "getAllNeighbors": "orange",
        "getDegree": "purple",
        "hasEdge": "red",
        "getRandomNeighbor": "cyan",
        "size": "brown"
    }

    OPERATION_MAP = {
        "getRandomNeighbor": "getRandomNeighbor(v)",
        "hasEdge": "hasEdge(v1,v2)",
        "getAllNeighbors": "getAllNeighbors()",
        "getDegree": "getDegree(v)",
        "size": "size()"
    }
    
    for value in left_y_value:
        if value == "runtime":
            plot_runtime(df, ax1, COLOR_MAP[value], window, "-", trendline, polynomial)
        elif value == "pathLength":
            plot_pathlength(df, ax1, COLOR_MAP[value], window, "-", trendline, polynomial)
        elif value in OPERATION_MAP:
            column = OPERATION_MAP[value]
            plot_operation(df, column, ax1, COLOR_MAP[value], window, "-", trendline, polynomial)

    for value in right_y_value:
        if value == "runtime":
            plot_runtime(df, ax2, COLOR_MAP[value], window, "--", trendline, polynomial)
        elif value == "pathLength":
            plot_pathlength(df, ax2, COLOR_MAP[value], window, "--", trendline, polynomial)
        elif value in OPERATION_MAP:
            column = OPERATION_MAP[value]
            plot_operation(df, column, ax2, COLOR_MAP[value], window, "--", trendline, polynomial)

    if left_unit:
        ax1.set_ylabel(f"({left_unit})")
    if right_y_value and right_unit:
        ax2.set_ylabel(f"({right_unit})")
    
    lines_left, labels_left = ax1.get_legend_handles_labels()
    all_lines, all_labels = lines_left, labels_left
    if right_y_value:
        lines_right, labels_right = ax2.get_legend_handles_labels()
        all_lines = all_lines + lines_right
        all_labels = all_labels + labels_right
    fig.legend(all_lines, all_labels, loc='upper center',
               bbox_to_anchor=(0.5, 0), ncol=2, borderaxespad=0)
    if title:
        fig.suptitle(title)
    plt.tight_layout()

    if save_to:
        file = f"{save_to}.png"
        plt.savefig(file, dpi=300, bbox_inches='tight')
        print(f"Plot saved to '{file}'.")
    else:
        plt.show()

def main():
    parser = argparse.ArgumentParser(
        description="Generate single run charts using data from a CSV file.",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog=(
            "examples:\n"
            "  python scripts\\SingleRunChartGenerator.py results.csv -l pathLength -r runtime -c\n"
            "  python scripts\\SingleRunChartGenerator.py results.csv -l getRandomNeighbor -r hasEdge size -ra 10\n"
            "  python scripts\\SingleRunChartGenerator.py results.csv -l runtime -r hasEdge -c -lu ms -ru count -s plot"
        )
    )

    parser.add_argument(
        "csv",
        metavar="CSV_FILE",
        help="Path to the CSV file."
    )

    parser.add_argument(
        "-l", "--left",
        metavar="LEFT_VALUES",
        nargs="+",
        choices=[
                    "runtime",
                    "pathLength",
                    "getAllNeighbors",
                    "getDegree",
                    "hasEdge",
                    "getRandomNeighbor",
                    "size"
                ],
        default=[],
        help="Plot type(s) to display on the left y-axis for double axis plot (choices: pathLength, runtime, getRandomNeighbor, hasEdge, getAllNeighbors, getDegree, size)."
    )

    parser.add_argument(
        "-r", "--right",
        metavar="RIGHT_VALUES",
        nargs="+",
        choices=[
                    "runtime",
                    "pathLength",
                    "getAllNeighbors",
                    "getDegree",
                    "hasEdge",
                    "getRandomNeighbor",
                    "size"
                ],
        default=[],
        help="Plot type(s) to display on the right y-axis for double axis plot (choices: pathLength, runtime, getRandomNeighbor, hasEdge, getAllNeighbors, getDegree, size)."
    )

    group = parser.add_mutually_exclusive_group(required=True)
    group.add_argument(
        "-c", "--cumulative",
        action="store_true",
        help="Create a cumulative plot."
    )
    group.add_argument(
        "-ra", "--rolling_avg",
        metavar="WINDOW_SIZE",
        type=int,
        default=None,
        help="Create a rolling average plot with a specific window size."
    )

    parser.add_argument(
        "-lu", "--left_unit",
        metavar="LEFT_UNIT",
        default="",
        help="Unit for the left y-axis (ex: 'ms', 'count')."
    )

    parser.add_argument(
        "-ru", "--right_unit",
        metavar="RIGHT_UNIT",
        default="",
        help="Unit for the right y-axis (ex: 'ms', 'count')."
    )

    parser.add_argument(
        "-s", "--save",
        metavar="FILE_NAME",
        help="Save the figure as a png instead of displaying it."
    )

    parser.add_argument(
        "-tr", "--trendline",
        action="store_true",
        help="Overlay a best-fit trendline on each plotted series."
    )

    parser.add_argument(
        "-p", "--polynomial",
        action="store_true",
        help="Include polynomial (n^k) as a trendline candidate (off by default)."
    )

    parser.add_argument(
        "-t", "--title",
        metavar="TITLE",
        default=None,
        help="Title to display above the chart."
    )

    args = parser.parse_args()

    if args.rolling_avg is not None and args.rolling_avg < 1:
        parser.error("Rolling average window size must be at least 1.")

    if args.cumulative:
        single_run_chart(args.csv, args.left, args.right, -1, args.left_unit, args.right_unit, args.save, args.trendline, args.polynomial, args.title)
    else:
        single_run_chart(args.csv, args.left, args.right, args.rolling_avg, args.left_unit, args.right_unit, args.save, args.trendline, args.polynomial, args.title)

if __name__=="__main__":
    main()