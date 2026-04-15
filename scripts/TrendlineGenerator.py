import numpy as np
from scipy.optimize import curve_fit
import matplotlib.pyplot as plt
import warnings

def draw_trendline(x, y):
    """
    Fits multiple curves to the given x and y data and returns the one with the highest R^2.
    
    :param x: array-like, independent variable data (e.g., matching size)
    :param y: array-like, dependent variable data (e.g., runtime)
    :return: dictionary containing the best fit curve's formula (name), parameters, R^2, and a predictor function
    """
    x = np.array(x, dtype=float)
    y = np.array(y, dtype=float)

    # Suppress log(0) or log(negative) warnings during optimization trials
    warnings.filterwarnings("ignore")

    # 1. Define the curve functions: c * f(a * x) + b
    def func_linear(x, a, b, c):
        return c * (a * x) + b

    def func_quad(x, a, b, c):
        return c * ((a * x)**2) + b

    def func_log(x, a, b, c):
        return c * np.log(a * x) + b

    def func_nlogn(x, a, b, c):
        return c * (a * x) * np.log(a * x) + b

    def func_poly(x, a, b, c, k):
        return c * ((a * x)**k) + b

    functions = {
        "Linear": func_linear,
        "Quadratic": func_quad,
        "Logarithmic (log n)": func_log,
        "N log N": func_nlogn,
        "Polynomial (n^k)": func_poly
    }

    best_fit = {
        "name": None,
        "params": None,
        "r2": -np.inf,
        "predictor": None
    }

    # rough guess (b): Lowest y-value
    guess_b = np.min(y)
    # rough guess (c): Rough slope from start to finish
    x_range = np.max(x) - np.min(x)
    guess_c = (np.max(y) - np.min(y)) / x_range if x_range != 0 else 1.0

    # 2. Iterate through each function and attempt a curve fit
    for name, func in functions.items():
        try:
            # create initial parameter guess, using the rough slope and lowest y-value
            if name == "Polynomial (n^k)":
                p0 = [1.0, guess_b, guess_c, 2.0] # a, b, c, k
            else:
                p0 = [1.0, guess_b, guess_c]      # a, b, c
            
            # maxfev = max # of function evaluations
            popt, _ = curve_fit(func, x, y, p0=p0, maxfev=10000)
            
            # 3. Calculate R-squared
            y_pred = func(x, *popt)
            ss_res = np.sum((y - y_pred)**2)       # Residual sum of squares
            ss_tot = np.sum((y - np.mean(y))**2)   # Total sum of squares
            
            # Avoid division by zero if y is a flat line
            if ss_tot == 0:
                r2 = 1.0 if ss_res == 0 else 0.0
            else:
                r2 = 1 - (ss_res / ss_tot)
            
            # 4. Check if this is the best curve so far
            if r2 > best_fit["r2"]:
                if name == "Linear":
                    a, b, c = popt
                    fmt_name = f"Linear: y = {c:.2f} * ({a:.2f}x) + {b:.2f}"
                elif name == "Quadratic":
                    a, b, c = popt
                    fmt_name = f"Quadratic: y = {c:.2f} * ({a:.2f}x)^2 + {b:.2f}"
                elif name == "Logarithmic (log n)":
                    a, b, c = popt
                    fmt_name = f"Log: y = {c:.2f} * log({a:.2f}x) + {b:.2f}"
                elif name == "N log N":
                    a, b, c = popt
                    fmt_name = f"N log N: y = {c:.2f} * ({a:.2f}x) * log({a:.2f}x) + {b:.2f}"
                elif name == "Polynomial (n^k)":
                    a, b, c, k = popt
                    fmt_name = f"Poly: y = {c:.2f} * ({a:.2f}x)^{k:.2f} + {b:.2f}"

                best_fit["name"] = fmt_name
                best_fit["params"] = popt
                best_fit["r2"] = r2
                best_fit["predictor"] = lambda x_vals, f=func, p=popt: f(x_vals, *p)
                
        except Exception:
            # If the curve fit fails to converge, skip it
            continue

    warnings.filterwarnings("default") # Turn warnings back on
    return best_fit

# Example usage
if __name__ == "__main__":
    # Generate some dummy data
    x_data = np.linspace(1, 100, 50)
    y_data = 10 * (x_data**0.4) + np.random.normal(0, 2, size=x_data.shape)

    # 1. Call the function
    best_curve = draw_trendline(x_data, y_data)

    # 2. Print the results
    print(f"Best fit: {best_curve['name']}")
    print(f"R^2 Score: {best_curve['r2']:.4f}")

    # 3. Plot the original data and the returned trendline
    plt.scatter(x_data, y_data, color='blue', label='Actual Data')
    
    if best_curve["predictor"]:
        # Use the returned predictor function to generate the smooth trendline
        x_smooth = np.linspace(min(x_data), max(x_data), 200)
        y_smooth = best_curve["predictor"](x_smooth)
        
        plt.plot(x_smooth, y_smooth, color='red', linewidth=2, 
                 label=best_curve["name"])

    plt.xlabel("Matching Size (N)")
    plt.ylabel("Runtime")
    plt.legend()
    plt.show()
