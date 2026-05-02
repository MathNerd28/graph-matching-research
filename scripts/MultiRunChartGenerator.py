import matplotlib.pyplot as plt
import numpy as np
import pandas as pd
import argparse
import re
import os
from TrendlineGenerator import draw_trendline

def plot_data(csvs, ax, ax_color, line_style, data_type, trendline=False, polynomial=False):
    """
    Plot the specified data against the matching size on the given axis (left or right).

    :param csvs: csvs files containing the data to plot
    :param ax: ax object to plot on
    :param ax_color: the color of the ax object
    :param line_style: the style of the line based on if its the left or right axis
    :param data_type: the type of data being plotted
    :param trendline: overlay a best-fit trendline if True
    """

    data = {}
    for csv in csvs:
        df = pd.read_csv(csv)
        size = extract_graph_size(csv)
        if size not in data:
            data[size] = [df[data_type].cumsum().iloc[-1], 1]
        else:
            data[size] = [data[size][0] + df[data_type].cumsum().iloc[-1], data[size][1] + 1]

    sizes = []
    values_to_plot = []
    for size, (total, count) in sorted(data.items()):
        sizes.append(size)
        values_to_plot.append(total / count)

    if data_type == "Iteration Time":
        ax.plot(sizes, values_to_plot, color=ax_color, label="runtime", linestyle=line_style, marker='o')
    elif data_type == "Path Length":
        ax.plot(sizes, values_to_plot, color=ax_color, label="pathLength", linestyle=line_style, marker='o')
    else:
        ax.plot(sizes, values_to_plot, color=ax_color, label=data_type, linestyle=line_style, marker='o')
    ax.tick_params(axis='y', colors=ax_color)

    if trendline and len(sizes) >= 4:
        fit = draw_trendline(np.array(sizes, dtype=float), np.array(values_to_plot, dtype=float), polynomial)
        if fit["predictor"]:
            x_s = np.linspace(min(sizes), max(sizes), 300)
            ax.plot(x_s, fit["predictor"](x_s), color=ax_color, linestyle=":",
                    alpha=0.7, label=f"{fit['name']} (R²={fit['r2']:.3f})")

def extract_graph_size(filepath):
    """
    Read the file name that is in a standardized format and return the graph size

    :param filepath: csv file path containing the data to plot
    """

    match = re.search(r'-(\d+)([kM]?)-\d+_\d+\.csv$', filepath, re.IGNORECASE)
    if not match:
        raise ValueError(f"Could not extract graph size from filename: {filepath}")
    
    size_str = match.group(1)
    suffix = match.group(2).lower()
    if 'm' in suffix:
        return int(size_str) * 1000000
    if 'k' in suffix:
        return int(size_str) * 1000
    return int(size_str)

def collect_csv_files(directory):
    """
    Returns the list of csv files from a specified directory

    :param directory: directory containing all the csv files
    """
    csv_files = []

    if os.path.isdir(directory):
        for root, _, files in os.walk(directory):
            for f in files:
                if f.endswith(".csv"):
                    csv_files.append(os.path.join(root, f))
    else:
        csv_files.append(directory)

    return csv_files

def multi_run_chart(csvs, left_y_value, right_y_value, left_unit, right_unit, save_to, log_scale, trendline=False, polynomial=False, title=None):
    """
    Based on the user input, generate a multi-run chart from the data in the specified CSV files

    :param csvs: csvs files containing the data to plot
    :param left_y_value: the values to plot on the left axis
    :param right_y_value: the values to plot on the right axis
    :param left_unit: the unit for the left axis
    :param right_unit: the unit for the right axis
    :param save_to: file name to save the generated plot (if None, the plot will be displayed instead)
    :param trendline: overlay a best-fit trendline on each series if True
    :param polynomial: include polynomial (n^k) as a candidate fit if True
    """
    
    fig, ax1 = plt.subplots()
    if right_y_value:
        ax2 = ax1.twinx()
    ax1.set_xlabel("Graph Size")

    if log_scale:
        ax1.set_xscale("log")

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
            plot_data(csvs, ax1, COLOR_MAP[value], "-", "Iteration Time", trendline, polynomial)
        elif value == "pathLength":
            plot_data(csvs, ax1, COLOR_MAP[value], "-", "Path Length", trendline, polynomial)
        elif value in OPERATION_MAP:
            column = OPERATION_MAP[value]
            plot_data(csvs, ax1, COLOR_MAP[value], "-", column, trendline, polynomial)

    for value in right_y_value:
        if value == "runtime":
            plot_data(csvs, ax2, COLOR_MAP[value], "--", "Iteration Time", trendline, polynomial)
        elif value == "pathLength":
            plot_data(csvs, ax2, COLOR_MAP[value], "--", "Path Length", trendline, polynomial)
        elif value in OPERATION_MAP:
            column = OPERATION_MAP[value]
            plot_data(csvs, ax2, COLOR_MAP[value], "--", column, trendline, polynomial)

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
        description="Generate multi run charts using data from CSV files.",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog=(
            "examples:\n"
            "  python scripts\\MultiRunChartGenerator.py results1.csv results2.csv results3.csv -l pathLength -r runtime\n"
            "  python scripts\\MultiRunChartGenerator.py results1.csv results2.csv results3.csv -l getRandomNeighbor -r hasEdge size\n"
            "  python scripts\\MultiRunChartGenerator.py results1.csv results2.csv results3.csv -l runtime -r hasEdge -lu ms -ru count -s plot"
        )
    )

    group = parser.add_mutually_exclusive_group(required=True)
    group.add_argument(
        "-c", "-csvs",
        metavar="CSV_FILES",
        nargs="+",
        help="Path to the CSV files."
    )

    group.add_argument(
        "-R", "--recursive",
        metavar="RECURSIVE",
        help="Recurse through a directory to find the csv files",
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
        "-log", "--log_scale",
        help="Use a log scale for the x-axis",
        action="store_true"
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
    if (args.recursive):
        multi_run_chart(
            collect_csv_files(args.recursive),
            args.left,
            args.right,
            args.left_unit,
            args.right_unit,
            args.save,
            args.log_scale,
            args.trendline,
            args.polynomial,
            args.title
        )
    else:
        multi_run_chart(
            args.c,
            args.left,
            args.right,
            args.left_unit,
            args.right_unit,
            args.save,
            args.log_scale,
            args.trendline,
            args.polynomial,
            args.title
        )

if __name__=="__main__":
    main()