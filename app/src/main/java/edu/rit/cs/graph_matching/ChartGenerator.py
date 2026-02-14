import matplotlib.pyplot as plt
import pandas as pd

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

        legend = col
        if col == "v2)":
            legend = "hasEdge(v1, v2)"
        
        ax.plot(df["Matching Size"], df[f"Cumulative {col}"], label=legend)

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

        legend = col
        if col == "v2)":
            legend = "hasEdge(v1, v2)"

        ax.plot(df["Matching Size"], df[f"Rolling Avg {col}"], label=legend)

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

def single_run_chart(csv_file_path, y_value, window=5):
    """
    Based on the user input, calculates the cumulative runtime, 
    cumulative operation counts, rolling average runtime, rolling 
    average operation counts, and rolling average path length 
    from the data in the specified CSV file.
    
    :param csv_file_path: csv file path containing the data to plot
    :param y_value: the type of plot the user wants to generate 
    :param window: window size for calculating the rolling average (default is 5)
    """

    df = pd.read_csv(csv_file_path)

    operation_columns = [
        "getAllNeighbors()",
        "getDegree(v)",
        "v2)",
        "getRandomNeighbor(v)"
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
        plt.show()
        return

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
    else:
        print(
            f"Unknown y_value: '{y_value}'"
            f"\nValid options: 'cumulative_runtime', 'cumulative_operations', "
            f"'rolling_avg_runtime', 'rolling_avg_operations', 'rolling_avg_path_length'"
        )
        return
    
    plt.grid(True)
    plt.show()

def main():
    single_run_chart(r"app\src\main\java\edu\rit\cs\graph_matching\test_large.csv", "all")

if __name__=="__main__":
    main()