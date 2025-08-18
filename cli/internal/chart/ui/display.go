package ui

import (
	"fmt"
	"io"

	"github.com/flamingo/openframe/internal/chart/domain"
	"github.com/pterm/pterm"
)

// DisplayService handles chart-related UI display operations
type DisplayService struct{}

// NewDisplayService creates a new display service
func NewDisplayService() *DisplayService {
	return &DisplayService{}
}

// ShowInstallProgress displays installation progress
func (d *DisplayService) ShowInstallProgress(chartType domain.ChartType, message string) {
	pterm.Info.Printf("📦 %s: %s\n", string(chartType), message)
}

// ShowInstallSuccess displays successful installation
func (d *DisplayService) ShowInstallSuccess(chartType domain.ChartType, info domain.ChartInfo) {
	fmt.Println()
	
	boxContent := fmt.Sprintf(
		"CHART:     %s\n"+
		"NAMESPACE: %s\n"+
		"STATUS:    %s\n"+
		"VERSION:   %s",
		pterm.Bold.Sprint(info.Name),
		info.Namespace,
		pterm.Green(info.Status),
		info.Version,
	)
	
	title := fmt.Sprintf(" ✅ %s Installed ", string(chartType))
	pterm.DefaultBox.
		WithTitle(title).
		WithTitleTopCenter().
		Println(boxContent)
}

// ShowInstallError displays installation error
func (d *DisplayService) ShowInstallError(chartType domain.ChartType, err error) {
	pterm.Error.Printf("Failed to install %s: %v\n", string(chartType), err)
}

// ShowPreInstallCheck displays pre-installation checks
func (d *DisplayService) ShowPreInstallCheck(message string) {
	pterm.Info.Printf("🔍 %s\n", message)
}

// ShowDryRunResults displays dry-run results
func (d *DisplayService) ShowDryRunResults(w io.Writer, results []string) {
	fmt.Fprintln(w)
	pterm.Info.Println("📋 Dry Run Results:")
	for _, result := range results {
		fmt.Fprintf(w, "  %s\n", result)
	}
}