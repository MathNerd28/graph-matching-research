import matplotlib.pyplot as plt
import pandas as pd
import argparse

def plot_cumulative_runtime(df, ax):
    """
    Calculates the cumulative runtime and plots 
    it against the matching size.
    
    :param df: data frame containing the data to plot
    :param ax: ax object to plot on
    """

    df["cumulative_runtime"] = df["Iteration Time"].cumsum()

    ax.plot(df["Matching Size"], df["cumulative_runtime"])
    ax.set_xlabel("Matching Size")
    ax.set_ylabel("Cumulative Runtime")
    ax.set_title("Cumulative Runtime vs Matching Size")

def plot_cumulative_operations(df, operation_columns, ax):
    """
    Calculates the cumulative counts for each operation 
    column and plots them against the matching size.
    
    :param df: data frame containing the data to plot
    :param operation_columns: the different operation names
    :param ax: ax object to plot on
    """

    for col in operation_columns:
        df[f"Cumulative {col}"] = df[col].cumsum()
        ax.plot(df["Matching Size"], df[f"Cumulative {col}"], label=col)

    ax.set_xlabel("Matching Size")
    ax.set_ylabel("Cumulative Operation Count")
    ax.set_title("Cumulative Operation Counts vs Matching Size")
    ax.legend(loc="upper left")

def plot_rolling_avg_runtime(df, window, ax):
    """
    Calculates the rolling average runtime using a specified 
    window size and plots it against the matching size.
    
    :param df: data frame containing the data to plot
    :param window: window size for calculating the rolling average
    :param ax: ax object to plot on
    """

    df["Rolling Avg Runtime"] = (df["Iteration Time"].rolling(window=window).mean())

    ax.plot(df["Matching Size"], df["Rolling Avg Runtime"])
    ax.set_xlabel("Matching Size")
    ax.set_ylabel("Runtime")
    ax.set_title(f"Rolling Average Runtime (window={window})")


def plot_rolling_avg_operations(df, operation_columns, window, ax):
    """
    Calculates the rolling average counts for each operation column
    using a specified window size and plots them against the matching size.
    
    :param df: data frame containing the data to plot
    :param operation_columns: the different operation names
    :param window: window size for calculating the rolling average
    :param ax: ax object to plot on
    """

    for col in operation_columns:
        df[f"Rolling Avg {col}"] = (df[col].rolling(window=window).mean())
        ax.plot(df["Matching Size"], df[f"Rolling Avg {col}"], label=col)

    ax.set_xlabel("Matching Size")
    ax.set_ylabel("Average Operation Count")
    ax.set_title(f"Rolling Average Operation Counts (window={window})")
    ax.legend(loc="upper left")


def plot_rolling_avg_path_length(df, window, ax):
    """
    Calculates the rolling average path length using a specified
    window size and plots it against the matching size.
    
    :param df: data frame containing the data to plot
    :param window: window size for calculating the rolling average
    :param ax: ax object to plot on
    """

    df["Rolling Avg Path Length"] = (df["Path Length"].rolling(window=window).mean())

    ax.plot(df["Matching Size"], df["Rolling Avg Path Length"])
    ax.set_xlabel("Matching Size")
    ax.set_ylabel("Path Length")
    ax.set_title(f"Rolling Average Path Length (window={window})")

def single_run_chart(csv_file_path, y_value, window, save_to):
    """
    Based on the user input, calculates the cumulative runtime, 
    cumulative operation counts, rolling average runtime, rolling 
    average operation counts, and rolling average path length 
    from the data in the specified CSV file.
    
    :param csv_file_path: csv file path containing the data to plot
    :param y_value: the type of plot the user wants to generate 
    :param window: window size for calculating the rolling average (default is 5)
    :param save_to: file path to save the generated plot (if None, the plot will be displayed instead)
    """

    df = pd.read_csv(csv_file_path)

    operation_columns = [
        "getAllNeighbors()",
        "getDegree(v)",
        "hasEdge(v1,v2)",
        "getRandomNeighbor(v)",
        "size()"
    ]

    if y_value == "all":
        fig, axes = plt.subplots(3, 2, figsize=(16, 12))
        axes = axes.flatten()

        plot_cumulative_runtime(df, axes[0])
        plot_cumulative_operations(df, operation_columns, axes[1])
        plot_rolling_avg_runtime(df, window, axes[2])
        plot_rolling_avg_operations(df, operation_columns, window, axes[3])
        plot_rolling_avg_path_length(df, window, axes[4])

        fig.delaxes(axes[5])

        for ax in axes:
            ax.grid(True)

        plt.tight_layout(pad=1.2)
    else:
        plt.figure(figsize=(10, 6))

        if y_value == "cumulative_runtime":
            plot_cumulative_runtime(df, plt.gca())
        elif y_value == "cumulative_operations":
            plot_cumulative_operations(df, operation_columns, plt.gca())
        elif y_value == "rolling_avg_runtime":
            plot_rolling_avg_runtime(df, window, plt.gca())
        elif y_value == "rolling_avg_operations": 
            plot_rolling_avg_operations(df, operation_columns, window, plt.gca())
        elif y_value == "rolling_avg_path_length":
            plot_rolling_avg_path_length(df, window, plt.gca())
        
        plt.grid(True)
    
    if save_to:
        plt.savefig(save_to, dpi=300, bbox_inches='tight')
        print(f"Plot saved to '{save_to}'.")
    else:
        plt.show()

def main():
    parser = argparse.ArgumentParser(
        description="Generate charts using data from a CSV file.",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog=(
            "examples:\n"
            "  python plot_matching.py results.csv all\n"
            "  python plot_matching.py results.csv rolling_avg_runtime -w 10\n"
            "  python plot_matching.py results.csv cumulative_runtime -s plot.png"
        )
    )

    parser.add_argument(
        "csv",
        metavar="CSV_FILE",
        help="Path to the CSV file."
    )

    parser.add_argument(
        "plot",
        metavar="PLOT_TYPE",
        choices=[
                    "all",
                    "cumulative_runtime",
                    "cumulative_operations",
                    "rolling_avg_runtime",
                    "rolling_avg_operations",
                    "rolling_avg_path_length"
                ],
        help=("Plot to generate: all, cumulative_runtime, cumulative_operations, rolling_avg_runtime, rolling_avg_operations, rolling_avg_path_length")
    )

    parser.add_argument(
        "-w", "--window",
        metavar="N",
        type=int,
        default=5,
        help="Rolling average window size (default: 5).",
    )

    parser.add_argument(
        "-s", "--save",
        metavar="FILE",
        default=None,
        help="Save the figure to a file instead of displaying it (e.g. plot.png).",
    )

    args = parser.parse_args()
    if args.window < 1:
        parser.error("window must be a positive integer.")
    single_run_chart(args.csv, args.plot, args.window, args.save)

if __name__=="__main__":
    main()