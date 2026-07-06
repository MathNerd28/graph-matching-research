"""
Plot dh-hard-test CSV output.

This script is intentionally specific to the Dani-Hayes hard construction. It
expects CSVs written by the `dh-hard-test` command and plots the measured
operation counts against the construction degree d, with an exponential fit.

Example:
  python scripts/DHHardPlot.py \\
      -d app/app/tmp/dh_hard_fit_run \\
      -s app/app/tmp/dh_hard_fit_run/dh_hard_exp
"""

import argparse
import os
import sys

import matplotlib.pyplot as plt
import numpy as np
import pandas as pd

sys.path.insert(0, os.path.dirname(__file__))
from TrendlineGenerator import EXPONENTIAL_MODEL, draw_trendline

RANDOM_NEIGHBOR = "getRandomNeighbor(v)"
GET_DEGREE = "getDegree(v)"


def collect_csvs(directory):
    csvs = []
    for root, _, files in os.walk(directory):
        for filename in files:
            if filename.endswith(".csv"):
                csvs.append(os.path.join(root, filename))
    return sorted(csvs)


def read_dh_hard_rows(csvs, x_axis):
    frames = []
    required = {"Degree", "Graph Size", RANDOM_NEIGHBOR, GET_DEGREE, "Status"}

    for path in csvs:
        df = pd.read_csv(path)
        missing = required.difference(df.columns)
        if missing:
            print(f"  Skipping {path}: missing {', '.join(sorted(missing))}")
            continue

        x_col = "Degree" if x_axis == "degree" else "Graph Size"
        keep = df[[x_col, RANDOM_NEIGHBOR, GET_DEGREE, "Status"]].copy()
        keep.rename(columns={x_col: "x"}, inplace=True)
        keep["x"] = pd.to_numeric(keep["x"], errors="coerce")
        keep[RANDOM_NEIGHBOR] = pd.to_numeric(keep[RANDOM_NEIGHBOR], errors="coerce")
        keep[GET_DEGREE] = pd.to_numeric(keep[GET_DEGREE], errors="coerce")
        keep.dropna(subset=["x", RANDOM_NEIGHBOR, GET_DEGREE], inplace=True)
        frames.append(keep)

    if not frames:
        return pd.DataFrame(columns=["x", RANDOM_NEIGHBOR, GET_DEGREE, "Status"])
    return pd.concat(frames, ignore_index=True)


def aggregate(rows):
    rows = rows.copy()
    rows["Total"] = rows[RANDOM_NEIGHBOR] + rows[GET_DEGREE]

    values = rows.groupby("x", as_index=False)[[RANDOM_NEIGHBOR, GET_DEGREE, "Total"]].mean()
    status_counts = rows.groupby(["x", "Status"]).size().unstack(fill_value=0)
    return values.sort_values("x"), status_counts.sort_index()


def add_exponential_fit(ax, x, y, color):
    if len(x) < 4:
        return None
    fit = draw_trendline(
        x,
        y,
        only={EXPONENTIAL_MODEL},
        exponential=True,
    )
    if fit["predictor"] is None:
        return None

    xs = np.linspace(x.min(), x.max(), 300)
    ax.plot(xs, fit["predictor"](xs), color=color, linestyle=":",
            alpha=0.75, label=f"{fit['name']} (R^2={fit['r2']:.3f})")
    return fit


def save_or_show(fig, path):
    if path:
        plt.savefig(path, dpi=300, bbox_inches="tight")
        print(f"  Saved {path}")
    else:
        plt.show()
    plt.close(fig)


def plot_series(values, column, title, ylabel, x_label, output_path, color):
    fig, ax = plt.subplots(figsize=(8, 5))
    x = values["x"].to_numpy(dtype=float)
    y = values[column].to_numpy(dtype=float)

    ax.plot(x, y, color=color, marker="o", label=column)
    add_exponential_fit(ax, x, y, color)
    ax.set_xlabel(x_label)
    ax.set_ylabel(ylabel)
    ax.set_title(title)
    ax.legend(fontsize=8, loc="upper left")
    plt.tight_layout()
    save_or_show(fig, output_path)


def print_summary(values, status_counts):
    print("Averaged DH hard measurements:")
    for _, row in values.iterrows():
        x = int(row["x"])
        statuses = status_counts.loc[x].to_dict() if x in status_counts.index else {}
        status_text = ", ".join(f"{name}={count}" for name, count in statuses.items())
        print(
            f"  x={x}: randomNeighbor={row[RANDOM_NEIGHBOR]:.0f}, "
            f"getDegree={row[GET_DEGREE]:.0f}, total={row['Total']:.0f}"
            + (f" ({status_text})" if status_text else "")
        )


def main():
    parser = argparse.ArgumentParser(description="Plot dh-hard-test operation counts.")
    parser.add_argument("-d", "--dir", required=True,
                        help="Directory containing dh-hard-test CSV files")
    parser.add_argument("-s", "--save", default=None,
                        help="Output file prefix; omit to display interactively")
    parser.add_argument("-x", "--x-axis", choices=("degree", "size"), default="degree",
                        help="Plot against construction degree d or graph size n")
    args = parser.parse_args()

    csvs = collect_csvs(args.dir)
    if not csvs:
        parser.error(f"No CSV files found in {args.dir}")

    rows = read_dh_hard_rows(csvs, args.x_axis)
    if rows.empty:
        parser.error("No dh-hard-test CSV rows with the required columns were found")

    values, status_counts = aggregate(rows)
    print_summary(values, status_counts)

    x_label = "Degree d" if args.x_axis == "degree" else "Graph Size n"
    prefix = args.save
    plot_series(
        values,
        RANDOM_NEIGHBOR,
        f"{RANDOM_NEIGHBOR} vs {x_label} -- dh-hard",
        f"{RANDOM_NEIGHBOR} call count",
        x_label,
        f"{prefix}_randomNeighbor.png" if prefix else None,
        "#1f77b4",
    )
    plot_series(
        values,
        GET_DEGREE,
        f"{GET_DEGREE} vs {x_label} -- dh-hard",
        f"{GET_DEGREE} call count",
        x_label,
        f"{prefix}_getDegree.png" if prefix else None,
        "#ff7f0e",
    )
    plot_series(
        values,
        "Total",
        f"Total Operations vs {x_label} -- dh-hard",
        "Total Operations",
        x_label,
        f"{prefix}_total.png" if prefix else None,
        "#2ca02c",
    )


if __name__ == "__main__":
    main()
