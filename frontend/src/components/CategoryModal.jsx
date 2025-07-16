import React, { useState, useEffect } from 'react';
import { X, Save, Loader2, TrendingUp, TrendingDown } from 'lucide-react';

export default function CategoryModal({ isOpen, onClose, onSave, category = null, transactionType = 'EXPENSE', loading = false }) {
  const [formData, setFormData] = useState({
    name: '',
    type: transactionType,
  });

  const [errors, setErrors] = useState({});
  const [submitting, setSubmitting] = useState(false);

  // Update form data when category prop changes (for editing)
  useEffect(() => {
    if (category) {
      setFormData({
        name: category.name || '',
        type: category.type || transactionType,
      });
    } else {
      setFormData({
        name: '',
        type: transactionType,
      });
    }
    setErrors({});
  }, [category, isOpen, transactionType]);

  const handleChange = (field, value) => {
    setFormData(prev => ({ ...prev, [field]: value }));
    // Clear error when user starts typing
    if (errors[field]) {
      setErrors(prev => ({ ...prev, [field]: '' }));
    }
  };

  const validateForm = () => {
    const newErrors = {};

    if (!formData.name.trim()) {
      newErrors.name = 'Category name is required';
    } else if (formData.name.trim().length < 2) {
      newErrors.name = 'Category name must be at least 2 characters';
    } else if (formData.name.trim().length > 50) {
      newErrors.name = 'Category name must be less than 50 characters';
    }

    if (!formData.type) {
      newErrors.type = 'Category type is required';
    }

    setErrors(newErrors);
    return Object.keys(newErrors).length === 0;
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    
    if (!validateForm()) {
      return;
    }

    try {
      setSubmitting(true);
      
      // Debug: Log the form data to see what's being sent
      console.log('Submitting category with data:', {
        ...formData,
        name: formData.name.trim(),
        type: formData.type,
        userId: 1, // Ensure userId is explicitly set
      });
      
      await onSave({
        ...formData,
        name: formData.name.trim(),
        type: formData.type, // Make sure type is included
        userId: 1, // Ensure userId is explicitly set
      });
      
      // Reset form only if creating a new category
      if (!category) {
        setFormData({
          name: '',
          type: transactionType,
        });
      }
      
      setErrors({});
      onClose();
    } catch (error) {
      console.error('Failed to save category:', error);
      setErrors({ submit: error.message || 'Failed to save category' });
    } finally {
      setSubmitting(false);
    }
  };

  const handleClose = () => {
    setErrors({});
    if (!category) {
      setFormData({
        name: '',
        type: transactionType,
      });
    }
    onClose();
  };

  if (!isOpen) return null;

  return (
    <div className="fixed inset-0 bg-black/50 flex items-center justify-center z-50 p-4">
      <div className="bg-gray-900 border border-gray-800 rounded-xl p-6 w-full max-w-md">
        <div className="flex items-center justify-between mb-6">
          <h2 className="text-xl font-semibold">
            {category ? 'Edit Category' : 'Add New Category'}
          </h2>
          <button
            onClick={handleClose}
            className="p-2 hover:bg-gray-800 rounded-lg transition-colors"
          >
            <X className="w-5 h-5" />
          </button>
        </div>

        <form onSubmit={handleSubmit} className="space-y-4">
          {/* Category Type Selection */}
          <div>
            <label className="block text-sm font-medium text-gray-300 mb-2">
              Category Type *
            </label>
            <div className="grid grid-cols-2 gap-2">
              <button
                type="button"
                onClick={() => handleChange('type', 'INCOME')}
                className={`px-3 py-2 rounded-lg border text-sm font-medium transition-colors flex items-center justify-center ${
                  formData.type === 'INCOME'
                    ? 'bg-green-700/20 text-green-400 border-green-500/30'
                    : 'bg-gray-800/50 text-gray-400 border-gray-700 hover:border-gray-600'
                }`}
              >
                <TrendingUp className="w-5 h-5" />
              </button>
              <button
                type="button"
                onClick={() => handleChange('type', 'EXPENSE')}
                className={`px-3 py-2 rounded-lg border text-sm font-medium transition-colors flex items-center justify-center ${
                  formData.type === 'EXPENSE'
                    ? 'bg-red-700/20 text-red-400 border-red-500/30'
                    : 'bg-gray-800/50 text-gray-400 border-gray-700 hover:border-gray-600'
                }`}
              >
                <TrendingDown className="w-5 h-5" />
              </button>
            </div>
            {errors.type && (
              <p className="text-red-400 text-xs mt-1">{errors.type}</p>
            )}
          </div>

          {/* Category Name */}
          <div>
            <label className="block text-sm font-medium text-gray-300 mb-2">
              Category Name *
            </label>
            <input
              type="text"
              value={formData.name}
              onChange={(e) => handleChange('name', e.target.value)}
              className={`w-full px-3 py-2 bg-gray-800/50 border rounded-lg focus:outline-none focus:border-violet-500 transition-colors ${
                errors.name ? 'border-red-500' : 'border-gray-700'
              }`}
              placeholder="Enter category name"
              autoFocus
            />
            {errors.name && (
              <p className="text-red-400 text-xs mt-1">{errors.name}</p>
            )}
          </div>

          {/* Submit Error */}
          {errors.submit && (
            <p className="text-red-400 text-sm">{errors.submit}</p>
          )}

          {/* Submit Button */}
          <div className="flex gap-3 pt-4">
            <button
              type="button"
              onClick={handleClose}
              className="flex-1 px-4 py-2 bg-gray-800 hover:bg-gray-700 text-gray-300 rounded-lg transition-colors"
            >
              Cancel
            </button>
            <button
              type="submit"
              disabled={submitting}
              className="flex-1 px-4 py-2 bg-gradient-to-r from-violet-500 to-purple-600 hover:from-violet-600 hover:to-purple-700 text-white rounded-lg transition-all disabled:opacity-50 disabled:cursor-not-allowed flex items-center justify-center gap-2"
            >
              {submitting ? (
                <>
                  <Loader2 className="w-4 h-4 animate-spin" />
                  Saving...
                </>
              ) : (
                <>
                  <Save className="w-4 h-4" />
                  {category ? 'Update Category' : 'Add Category'}
                </>
              )}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
