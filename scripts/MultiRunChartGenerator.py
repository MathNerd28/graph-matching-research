import matplotlib.pyplot as plt
import pandas as pd
import argparse
import re

def plot_runtime(csvs, ax, ax_color, line_style):
    data = {}
    for csv in csvs:
        df = pd.read_csv(csv)
        size = extract_graph_size(csv)
        if size not in data:
            data[size] = [df["Iteration Time"].cumsum().iloc[-1], 1]
        else:
            data[size] = [data[size][0] + df["Iteration Time"].cumsum().iloc[-1], data[size][1] + 1]
    
    sizes = []
    runtimes = []
    for size, (total, count) in sorted(data.items()):
        sizes.append(size)
        runtimes.append(total / count)
    
    ax.plot(sizes, runtimes, color=ax_color, label="runtime", linestyle=line_style, marker='o')
    ax.tick_params(axis='y', colors=ax_color)

def plot_operation(csvs, operation, ax, ax_color, line_style):
    data = {}
    for csv in csvs:
        df = pd.read_csv(csv)
        size = extract_graph_size(csv)
        if size not in data:
            data[size] = [df[operation].cumsum().iloc[-1], 1]
        else:
            data[size] = [data[operation][0] + df["Iteration Time"].cumsum().iloc[-1], data[size][1] + 1]
    
    sizes = []
    operation_count = []
    for size, (total, count) in sorted(data.items()):
        sizes.append(size)
        operation_count.append(total / count)
    
    ax.plot(sizes, operation_count, color=ax_color, label=operation, linestyle=line_style, marker='o')
    ax.tick_params(axis='y', colors=ax_color)

def plot_pathlength(csvs, ax, ax_color, line_style):
    data = {}
    for csv in csvs:
        df = pd.read_csv(csv)
        size = extract_graph_size(csv)
        if size not in data:
            data[size] = [df["Path Length"].cumsum().iloc[-1], 1]
        else:
            data[size] = [data["Path Length"][0] + df["Iteration Time"].cumsum().iloc[-1], data[size][1] + 1]
    
    sizes = []
    path_length = []
    for size, (total, count) in sorted(data.items()):
        sizes.append(size)
        path_length.append(total / count)
    
    ax.plot(sizes, path_length, color=ax_color, label="pathLength", linestyle=line_style, marker='o')
    ax.tick_params(axis='y', colors=ax_color)

def extract_graph_size(filepath):
    match = re.search(r'-(10M|1M|100k|10k|1k|100|10)', filepath, re.IGNORECASE)
    if not match:
        raise ValueError(f"Could not extract graph size from filename: {filepath}")
    
    size_str = match.group(1).lower()
    if 'm' in size_str:
        return int(size_str[:-1]) * 1000000
    if 'k' in size_str:
        return int(size_str[:-1]) * 1000
    return int(size_str)

def multi_run_chart(csvs, left_y_value, right_y_value, left_unit, right_unit, save_to):
    fig, ax1 = plt.subplots()
    if right_y_value:
        ax2 = ax1.twinx()
    ax1.set_xlabel("Graph Size")

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
            plot_runtime(csvs, ax1, COLOR_MAP[value], "-")
        elif value == "pathLength":
            plot_pathlength(csvs, ax1, COLOR_MAP[value], "-")
        elif value in OPERATION_MAP:
            column = OPERATION_MAP[value]
            plot_operation(csvs, column, ax1, COLOR_MAP[value], "-")

    for value in right_y_value:
        if value == "runtime":
            plot_runtime(csvs, ax2, COLOR_MAP[value], "--")
        elif value == "pathLength":
            plot_pathlength(csvs, ax2, COLOR_MAP[value], "--")
        elif value in OPERATION_MAP:
            column = OPERATION_MAP[value]
            plot_operation(csvs, column, ax2, COLOR_MAP[value], "--")

    if left_unit:
        ax1.set_ylabel(f"({left_unit})")
    if right_y_value and right_unit:
        ax2.set_ylabel(f"({right_unit})")
    
    lines_left, labels_left = ax1.get_legend_handles_labels()
    ax1.legend(lines_left, labels_left, loc='upper left', title="Left Axis")

    if right_y_value:
        lines_right, labels_right = ax2.get_legend_handles_labels()
        ax2.legend(lines_right, labels_right, loc='upper right', title="Right Axis")

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

    parser.add_argument(
        "csvs",
        metavar="CSV_FILES",
        nargs="+",
        help="Path to the CSV files."
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

    args = parser.parse_args()
    for csv in args.csvs:
        print(csv, extract_graph_size(csv))
    multi_run_chart(args.csvs, args.left, args.right, args.left_unit, args.right_unit, args.save)

if __name__=="__main__":
    main()