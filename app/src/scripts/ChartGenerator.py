import matplotlib.pyplot as plt
import pandas as pd
import argparse

def plot_runtime(df, ax, ax_color, window_size, line_style):
    if (window_size == -1):
        df["Runtime"] = df["Iteration Time"].cumsum()
    else:
        df["Runtime"] = df["Iteration Time"].rolling(window_size).mean()

    ax.plot(df["Matching Size"], df["Runtime"], color=ax_color, label="runtime", linestyle=line_style)
    ax.tick_params(axis='y', colors=ax_color)

def plot_operation(df, operation, ax, ax_color, window_size, line_style):
    if (window_size == -1):
        df[f"{operation} Count"] = df[operation].cumsum()
    else:
        df[f"{operation} Count"] = df[operation].rolling(window_size).mean()

    if ax_color == "blue":
        ax.plot(df["Matching Size"], df[f"{operation} Count"], color=ax_color, label=operation, linestyle=line_style)
    else:
        ax.plot(df["Matching Size"], df[f"{operation} Count"], color=ax_color, label=operation, linestyle=line_style)
    ax.tick_params(axis='y', colors=ax_color)

def plot_pathlength(df, ax, ax_color, window_size, line_style):
    if (window_size == -1):
        df["Path Length"] = df["Path Length"].cumsum()
    else:
        df["Rolling Avg Path Length"] = (df["Path Length"].rolling(window_size).mean())
    
    ax.plot(df["Matching Size"], df["Path Length"], color=ax_color, label="pathLength", linestyle=line_style)
    ax.tick_params(axis='y', colors=ax_color)

def single_run_chart(csv_file_path, left_y_value, right_y_value, window, left_unit, right_unit, save_to):
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
            plot_runtime(df, ax1, COLOR_MAP[value], window, "-")
        elif value == "pathLength":
            plot_pathlength(df, ax1, COLOR_MAP[value], window, "-")
        elif value in OPERATION_MAP:
            column = OPERATION_MAP[value]
            plot_operation(df, column, ax1, COLOR_MAP[value], window, "-")

    for value in right_y_value:
        if value == "runtime":
            plot_runtime(df, ax2, COLOR_MAP[value], window, "--")
        elif value == "pathLength":
            plot_pathlength(df, ax2, COLOR_MAP[value], window, "--")
        elif value in OPERATION_MAP:
            value = OPERATION_MAP[value]
            plot_operation(df, value, ax2, COLOR_MAP[value], window, "--")

    if left_unit:
        ax1.set_ylabel(f"({left_unit})")
    if right_unit:
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
        description="Generate charts using data from a CSV file.",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog=(
            "examples:\n"
            "  python app\src\scripts\ChartGenerator.py results.csv all\n"
            "  python app\src\scripts\ChartGenerator.py results.csv rolling_avg_runtime -w 10\n"
            "  python app\src\scripts\ChartGenerator.py results.csv cumulative_runtime -s plot.png"
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

    args = parser.parse_args()


    if args.cumulative and args.rolling_avg:
        parser.error("Cannot specify both cumulative and rolling average options.")
    if args.rolling_avg is not None and args.rolling_avg < 1:
        parser.error("Rolling average window size must be at least 1.")

    if args.cumulative:
        single_run_chart(args.csv, args.left, args.right, -1, args.left_unit, args.right_unit, args.save)
    else:
        single_run_chart(args.csv, args.left, args.right, args.rolling_avg, args.left_unit, args.right_unit, args.save)

if __name__=="__main__":
    main()