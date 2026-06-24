import numpy as np
from scipy.optimize import curve_fit
import matplotlib.pyplot as plt
import warnings


def draw_trendline(x, y, polynomial=False):
    """
    Fits multiple curves to the given x and y data and returns the one with the
    lowest BIC (Bayesian Information Criterion). c.f. https://en.wikipedia.org/wiki/Bayesian_information_criterion
    BIC penalises each additional free parameter by log(n), which prevents n^k from overfitting data that is
    truly n^2: the exponent k gives the polynomial model one extra parameter
    whose BIC cost is only justified when the RSS improvement is substantial.

    Candidate models
    ----------------
    Linear          y = A*x + b
    Quadratic       y = A*x^2 + b
    Logarithmic     y = A*log(x) + b
    N log N         y = c*(a*x)*log(a*x) + b    (a is non-redundant: adds an x term)
    Polynomial n^k  y = A*x^k + b               (k free, bounds [0.1, 10])
    Harmonic        y = c/(x - s) + b           (s < min(x) enforced)
    Harmonic n/(N-n) y = c/(x - N) + b          (N > max(x) enforced)

    :param x: array-like, independent variable data (e.g., matching size)
    :param y: array-like, dependent variable data (e.g., runtime)
    :return: dictionary containing the best fit curve's formula (name),
             parameters, R^2, and a predictor function
    """
    x = np.array(x, dtype=float)
    y = np.array(y, dtype=float)
    n_pts = len(x)
    x_min = float(np.min(x))
    x_max = float(np.max(x))
    y_min = float(np.min(y))
    y_max = float(np.max(y))

    warnings.filterwarnings("ignore")

    # ------------------------------------------------------------------ #
    # Curve functions                                                      #
    # ------------------------------------------------------------------ #

    def func_linear(x, A, b):
        return A * x + b

    def func_quad(x, A, b):
        return A * x**2 + b

    def func_log(x, A, b):
        return A * np.log(x) + b

    # a is NOT redundant: c*(ax)*log(ax) expands to ca*x*log(x) + ca*log(a)*x + b,
    # so a independently controls the ratio between the x*log(x) and x terms.
    def func_nlogn(x, a, b, c):
        return c * (a * x) * np.log(a * x) + b

    def func_poly(x, A, k, b):
        return A * x**k + b

    def func_harmonic(x, c, s, b):
        return c / (x - s) + b

    # Models cumulative quantities of the form -A*log(N-x)+b, where N is the
    # total graph size (fitted).  As x → N the curve diverges to +∞, matching
    # the explosion seen in cumulative path-length / operation-count plots.
    def func_log_complement(x, A, N, b):
        return -A * np.log(N - x) + b

    # ------------------------------------------------------------------ #
    # Initial guesses                                                      #
    # ------------------------------------------------------------------ #

    x_range = x_max - x_min
    EPS = 1e-9

    guess_A_linear = (y_max - y_min) / x_range if x_range != 0 else 1.0
    guess_A_quad   = (y_max - y_min) / x_max**2 if x_max != 0 else 1.0
    log_range      = np.log(x_max / x_min) if x_min > 0 and x_max > x_min else 1.0
    guess_A_log    = (y_max - y_min) / log_range if log_range != 0 else 1.0
    # nlogn scale guess: c such that c*x_max*log(x_max) ≈ y_max - y_min (at a=1)
    guess_c_nlogn  = (y_max - y_min) / (x_max * np.log(x_max)) if x_max > 1 else 1.0

    def _logc_guess(N_val):
        """Return [A, N, b] initial guess for -A*log(N-x)+b at a given N seed."""
        if N_val <= x_max:
            return [1.0, x_max * 1.1, y_min]
        denom = float(np.log(N_val - x_min) - np.log(N_val - x_max))
        A_g = (y_max - y_min) / max(denom, EPS)
        b_g = y_min + A_g * float(np.log(N_val - x_min))
        return [max(A_g, EPS), N_val, b_g]

    # ------------------------------------------------------------------ #
    # Candidates: (func, list_of_p0, bounds)                              #
    # Multiple p0 entries give multi-start fitting for harder models.     #
    # ------------------------------------------------------------------ #

    candidates = {
        "Linear": (
            func_linear,
            [[guess_A_linear, y_min]],
            (-np.inf, np.inf),
        ),
        "Quadratic": (
            func_quad,
            [[guess_A_quad, y_min]],
            (-np.inf, np.inf),
        ),
        "Logarithmic (log n)": (
            func_log,
            [[guess_A_log, y_min], [1.0, 0.0]],
            (-np.inf, np.inf),
        ),
        "N log N": (
            func_nlogn,
            [[1.0, y_min, guess_c_nlogn], [1.0, 0.0, 1.0]],
            ([EPS, -np.inf, -np.inf], [np.inf, np.inf, np.inf]),
        ),
        # Multiple k seeds so the optimiser doesn't get trapped near k=2
        # when the true exponent is different.
        "Polynomial (n^k)": (
            func_poly,
            [[guess_A_quad, k, y_min] for k in [1.0, 1.5, 2.0, 2.5, 3.0]],
            ([-np.inf, 0.1, -np.inf], [np.inf, 10.0, np.inf]),
        ),
        # s is bounded strictly below x_min so the denominator never hits 0
        # inside the data range.
        "Harmonic (c/(n-s))": (
            func_harmonic,
            [
                [y_max * x_min,  0.0,            y_min],
                [y_max * x_min, -x_min,           y_min],
                [guess_A_linear * x_min, x_min / 2.0, y_min],
            ],
            ([-np.inf, -np.inf, -np.inf], [np.inf, x_min - EPS, np.inf]),
        ),
        # Singularity above the data range: models path lengths of the form
        # n / (n - s) where n is the total graph size (unknown, fitted as N)
        # and s is the matching size (x-axis).  Rewriting: n/(n-x) = -n/(x-n),
        # so this is c/(x-N) with c<0 and N>max(x).  The denominator is always
        # negative inside the data range, so c comes out negative and the curve
        # rises toward the singularity as x approaches n.
        "Harmonic (n/(N-n))": (
            func_harmonic,
            [
                # N = 2*x_max: c ≈ -(y_range)*(x_max*2 - x_max) = -(y_range)*x_max
                [-(y_max - y_min) * x_max, x_max * 2.0, y_min],
                # N = 1.5*x_max
                [-(y_max - y_min) * x_max * 0.5, x_max * 1.5, y_min],
                # N = 1.2*x_max (singularity close above the data)
                [-(y_max - y_min) * x_max * 0.2, x_max * 1.2, y_min],
            ],
            ([-np.inf, x_max + EPS, -np.inf], [np.inf, np.inf, np.inf]),
        ),
        # Cumulative quantities that diverge as matching size → graph size N.
        # Multiple N seeds: close (1.1x), moderate (1.5x), far (2x, 3x).
        "Log complement (-A·log(N-n))": (
            func_log_complement,
            [_logc_guess(x_max * f) for f in [1.1, 1.5, 2.0, 3.0]],
            ([EPS, x_max + EPS, -np.inf], [np.inf, np.inf, np.inf]),
        ),
    }

    # ------------------------------------------------------------------ #
    # Metrics                                                              #
    # ------------------------------------------------------------------ #

    def compute_r2(y_true, y_pred):
        ss_res = np.sum((y_true - y_pred) ** 2)
        ss_tot = np.sum((y_true - np.mean(y_true)) ** 2)
        if ss_tot == 0:
            return 1.0 if ss_res < 1e-30 else 0.0
        return 1.0 - ss_res / ss_tot

    def compute_bic(y_true, y_pred, n_params):
        """
        BIC = n*log(RSS/n) + p*log(n)  (lower is better).

        Penalises each free parameter by log(n) relative to the data-fit term.
        For n=100, one extra parameter costs ~4.6 BIC units, so the polynomial's
        free exponent k (3 params total vs quadratic's 2) only pays off when the
        RSS improvement is substantial — which it is for n^2.5 data but not for
        n^2 data where k collapses to ~2.
        """
        ss_res = max(float(np.sum((y_true - y_pred) ** 2)), 1e-30)
        return n_pts * np.log(ss_res / n_pts) + n_params * np.log(n_pts)

    # ------------------------------------------------------------------ #
    # Multi-start fit helper                                               #
    # ------------------------------------------------------------------ #

    def best_fit_multi(func, p0_list, bounds):
        best = None
        best_bic_local = np.inf
        for p0 in p0_list:
            try:
                popt, _ = curve_fit(
                    func, x, y, p0=p0, bounds=bounds, maxfev=10000
                )
                y_pred = func(x, *popt)
                if not np.all(np.isfinite(y_pred)):
                    continue
                bic = compute_bic(y, y_pred, len(popt))
                r2  = compute_r2(y, y_pred)
                if bic < best_bic_local:
                    best_bic_local = bic
                    best = {"popt": popt, "r2": r2, "bic": bic} # popt = parameters optimaized
            except Exception:
                continue
        return best

    # ------------------------------------------------------------------ #
    # Format display label                                                 #
    # ------------------------------------------------------------------ #

    def fmt(v):
        """Format a coefficient readably: fixed for normal range, scientific otherwise."""
        a = abs(v)
        if a == 0:
            return "0"
        if 0.01 <= a < 10000:
            return f"{v:.2f}"
        return f"{v:.2e}"

    def format_name(name, popt):
        if name == "Linear":
            A, b = popt
            return f"Linear: y = {fmt(A)}x + {fmt(b)}"
        elif name == "Quadratic":
            A, b = popt
            return f"Quadratic: y = {fmt(A)}x^2 + {fmt(b)}"
        elif name == "Logarithmic (log n)":
            A, b = popt
            return f"Log: y = {fmt(A)} * log(x) + {fmt(b)}"
        elif name == "N log N":
            a, b, c = popt
            return f"N log N: y = {fmt(c)} * ({fmt(a)}x) * log({fmt(a)}x) + {fmt(b)}"
        elif name == "Polynomial (n^k)":
            A, k, b = popt
            return f"Poly: y = {fmt(A)}x^{k:.2f} + {fmt(b)}"
        elif name == "Harmonic (c/(n-s))":
            c_h, s, b = popt
            return f"Harmonic: y = {fmt(c_h)} / (x - {fmt(s)}) + {fmt(b)}"
        elif name == "Harmonic (n/(N-n))":
            c_h, s, b = popt
            # c is negative; rewrite c/(x-N) as |c|/(N-x) for readability
            return f"Harmonic: y = {fmt(-c_h)} / ({fmt(s)} - x) + {fmt(b)}"
        elif name == "Log complement (-A·log(N-n))":
            A, N, b = popt
            return f"Log complement: y = -{fmt(A)} * log({fmt(N)} - x) + {fmt(b)}"
        return name

    # ------------------------------------------------------------------ #
    # Fit all candidates, select by lowest BIC                            #
    # ------------------------------------------------------------------ #

    best_fit = {"name": None, "params": None, "r2": -np.inf, "predictor": None}
    best_bic_global = np.inf

    if not polynomial:
        candidates.pop("Polynomial (n^k)", None)

    for name, (func, p0_list, bounds) in candidates.items():
        result = best_fit_multi(func, p0_list, bounds)
        if result is None:
            continue
        if result["bic"] < best_bic_global:
            best_bic_global = result["bic"]
            best_fit = {
                "name":      format_name(name, result["popt"]),
                "params":    result["popt"],
                "r2":        result["r2"],
                "predictor": lambda x_vals, f=func, p=result["popt"]: f(x_vals, *p),
            }

    warnings.filterwarnings("default")
    return best_fit


if __name__ == "__main__":
    rng = np.random.default_rng(42)

    # ── Helpers ──────────────────────────────────────────────────────── #

    def noisy(y, frac=0.01):
        """Add Gaussian noise scaled to `frac` of the signal range."""
        sigma = max((float(y.max()) - float(y.min())) * frac, 1e-6)
        return y + rng.normal(0, sigma, len(y))

    def match_at_xmax(x, k, y_ref):
        """Scale x^k so its value at x[-1] equals y_ref[-1].
        This removes magnitude as a distinguishing feature, leaving only shape."""
        return (float(y_ref[-1]) / x[-1] ** k) * x ** k

    def run_group(title, cases):
        print(f"\n{title}")
        print("─" * 78)
        for desc, (x, y, expected) in cases.items():
            best = draw_trendline(x, y)
            tag = "OK  " if expected.lower() in best["name"].lower() else "FAIL"
            print(f"  [{tag}]  {desc:<40s}  {best['name']:<42s}  R²={best['r2']:.4f}")

    # ── x ranges ─────────────────────────────────────────────────────── #
    x_100  = np.linspace(1,   100,  50)
    x_1000 = np.linspace(10, 1000,  80)
    x_500  = np.linspace(10,  500,  70)

    # ── Group 1: Baseline (x ∈ [1, 100]) ─────────────────────────────── #
    run_group("Baseline  (x ∈ [1, 100], 1 % noise)", {
        "n^2":     (x_100, noisy(10 * x_100**2),               "quadratic"),
        "n^2.5":   (x_100, noisy(0.5 * x_100**2.5),            "poly"),
        "n*logn":  (x_100, noisy(3 * x_100 * np.log(x_100)),   "n log n"),
        "1/n":     (x_100, noisy(5000 / x_100),                "harmonic"),
        "n/(N-n)": (x_100, noisy(120 / (120 - x_100)),         "harmonic"),
    })

    # ── Group 2: n log n vs n^k ───────────────────────────────────────── #
    # Curves are scaled to the same value at x=1000 so the only           #
    # distinguishing signal is their different shapes over [10, 1000].    #
    #                                                                      #
    # Inherent ambiguity: n^1.x and n*log(n) are genuinely hard to        #
    # separate for small exponents. In [10, 1000] the ratio               #
    # n^0.1 / log(n) only varies from 0.55 to 0.29, so n^1.1 and         #
    # n^1.2 matched to nlogn achieve the same BIC with 1 % noise.         #
    # Reliable discrimination starts around n^1.5 where the power-law     #
    # curvature is clearly distinct from the logarithmic growth factor.   #
    nlogn = x_1000 * np.log(x_1000)
    run_group("n log n  vs  n^k  (x ∈ [10, 1000], 1 % noise; matched at x = 1000)", {
        "n*logn":                    (x_1000, noisy(nlogn),                             "n log n"),
        "n^1.5  (matched to nlogn)": (x_1000, noisy(match_at_xmax(x_1000, 1.5, nlogn)), "poly"),
        "n^2    (matched to nlogn)": (x_1000, noisy(match_at_xmax(x_1000, 2.0, nlogn)), "quadratic"),
        "n^2.5  (matched to nlogn)": (x_1000, noisy(match_at_xmax(x_1000, 2.5, nlogn)), "poly"),
    })

    # ── Group 3: Close polynomial exponents ──────────────────────────── #
    # All curves matched to n^2 at x=1000; 1 % noise is sufficient        #
    # because the exponent gap creates a visible shape difference at       #
    # small x even after endpoint matching.                                #
    n2 = x_1000 ** 2
    run_group("Close polynomial exponents  (x ∈ [10, 1000], 1 % noise; matched at x = 1000)", {
        "n^2":                     (x_1000, noisy(n2),                              "quadratic"),
        "n^1.8  (matched to n^2)": (x_1000, noisy(match_at_xmax(x_1000, 1.8, n2)), "poly"),
        "n^2.2  (matched to n^2)": (x_1000, noisy(match_at_xmax(x_1000, 2.2, n2)), "poly"),
        "n^2.5  (matched to n^2)": (x_1000, noisy(match_at_xmax(x_1000, 2.5, n2)), "poly"),
        "n^3    (matched to n^2)": (x_1000, noisy(match_at_xmax(x_1000, 3.0, n2)), "poly"),
    })

    # ── Group 4: Sublinear curves ─────────────────────────────────────── #
    run_group("Sublinear  (x ∈ [10, 500], 1 % noise)", {
        "log(n)":   (x_500, noisy(20 * np.log(x_500)),  "log"),
        "n^0.5":    (x_500, noisy(4  * x_500**0.5),     "poly"),
        "n^0.3":    (x_500, noisy(15 * x_500**0.3),     "poly"),
        "linear":   (x_500, noisy(0.5 * x_500),         "linear"),
    })

    # ── Group 5: Harmonic variants ────────────────────────────────────── #
    # Use x up to 80 % of the pole location so both curves are            #
    # clearly curved (not just vaguely positive) within the data range.   #
    x_harm = np.linspace(10, 80, 50)   # pole at N=100, data stops at 0.8*N
    run_group("Harmonic variants  (x ∈ [10, 80], pole at N = 100, 1 % noise)", {
        "1/n         (pole below data)": (x_harm, noisy(500  / x_harm),             "harmonic"),
        "n/(N-n)     (pole above data)": (x_harm, noisy(100  / (100 - x_harm)),     "harmonic"),
        "5*n/(N-n)   (scaled)":          (x_harm, noisy(5 * 100 / (100 - x_harm)),  "harmonic"),
    })

    # ── Plot last test case ───────────────────────────────────────────── #
    print()
    x_plot, y_plot = x_harm, noisy(100 / (100 - x_harm))
    best = draw_trendline(x_plot, y_plot)
    plt.scatter(x_plot, y_plot, color="blue", label="Actual Data")
    if best["predictor"]:
        x_smooth = np.linspace(min(x_plot), max(x_plot), 200)
        plt.plot(x_smooth, best["predictor"](x_smooth), color="red",
                 linewidth=2, label=best["name"])
    plt.xlabel("Matching Size (N)")
    plt.ylabel("Runtime")
    plt.legend()
    plt.show()
